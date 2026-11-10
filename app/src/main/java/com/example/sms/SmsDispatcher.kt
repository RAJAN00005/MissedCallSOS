package com.example.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.entity.OutboxSmsEntity
import com.example.data.repository.SosRepository
import com.example.receiver.SmsStatusReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SmsDispatcher {
    const val MAX_SMS_PER_CONTACT_PER_HOUR = 3

    fun buildEmergencySmsMessage(missedCount: Int): String {
        return "SOS: I missed your $missedCount emergency calls! Please call back. -MissedCall SOS"
    }

    suspend fun queueEmergencySms(
        context: Context,
        repository: SosRepository,
        recipientNumber: String,
        missedCount: Int,
        triggerHistoryId: Long?,
        isTest: Boolean = false
    ): Boolean {
        // 1. Check opt-out
        val rule = repository.getRuleForNumber(recipientNumber)
        if (rule?.isOptedOut == true) {
            return false
        }

        // 2. Check rate limit: max 3 sends per recipient per hour (skip if isTest)
        if (!isTest) {
            val oneHourAgo = System.currentTimeMillis() - 3600_000L
            val recentCount = repository.countSmsSentToRecipientSince(recipientNumber, oneHourAgo)
            if (recentCount >= MAX_SMS_PER_CONTACT_PER_HOUR) {
                return false
            }
        }

        // 3. Build message text
        val messageText = buildEmergencySmsMessage(missedCount)
        val simSlot = repository.preferences.getSimSlotForSms()

        val outboxItem = OutboxSmsEntity(
            recipientNumber = recipientNumber,
            messageBody = messageText,
            simSlot = simSlot,
            triggerHistoryId = triggerHistoryId,
            status = "SENDING",
            isTest = isTest
        )
        val outboxId = repository.queueOutboxSms(outboxItem)

        // 4. Dispatch via telephony
        val dispatched = sendSmsImmediately(context, outboxId, recipientNumber, messageText, simSlot)
        if (!dispatched) {
            // Telephony call threw immediately; mark failed and schedule retry
            repository.updateOutboxSmsStatus(outboxId, "FAILED", "Telephony dispatch error")
            scheduleOutboxWorker(context)
        }

        return dispatched
    }

    fun sendSmsImmediately(
        context: Context,
        outboxId: Long,
        recipientNumber: String,
        messageBody: String,
        preferredSimSlot: Int
    ): Boolean {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val smsManager: SmsManager = getSmsManagerForSim(context, preferredSimSlot)

            val sentIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                action = SmsStatusReceiver.ACTION_SMS_SENT
                putExtra(SmsStatusReceiver.EXTRA_OUTBOX_ID, outboxId)
            }
            val sentPI = PendingIntent.getBroadcast(
                context,
                outboxId.toInt(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveryIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                putExtra(SmsStatusReceiver.EXTRA_OUTBOX_ID, outboxId)
            }
            val deliveryPI = PendingIntent.getBroadcast(
                context,
                (outboxId + 100000).toInt(),
                deliveryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(messageBody)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(sentPI)
                    deliveryIntents.add(deliveryPI)
                }
                smsManager.sendMultipartTextMessage(recipientNumber, null, parts, sentIntents, deliveryIntents)
            } else {
                smsManager.sendTextMessage(recipientNumber, null, messageBody, sentPI, deliveryPI)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getSmsManagerForSim(context: Context, simSlot: Int): SmsManager {
        if (simSlot >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subList = subManager?.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty()) {
                    val matchingSub = subList.firstOrNull { it.simSlotIndex == simSlot }
                    if (matchingSub != null) {
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java).createForSubscriptionId(matchingSub.subscriptionId)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getSmsManagerForSubscriptionId(matchingSub.subscriptionId)
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Fall through to default
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    fun scheduleOutboxWorker(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "outbox_retry_work",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }
}
