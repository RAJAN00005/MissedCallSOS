package com.example.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository

class OutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val preferences = AppPreferences(applicationContext)
        val repository = SosRepository(database, preferences)

        val pendingList = repository.getPendingOutboxSms()
        if (pendingList.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false
        for (sms in pendingList) {
            // Check retry limit: 5 attempts
            if (sms.retryCount >= 5) {
                repository.updateOutboxSmsStatus(sms.id, "FAILED", "Max retries exceeded")
                continue
            }

            val sent = SmsDispatcher.sendSmsImmediately(
                context = applicationContext,
                outboxId = sms.id,
                recipientNumber = sms.recipientNumber,
                messageBody = sms.messageBody,
                preferredSimSlot = sms.simSlot
            )

            if (sent) {
                repository.updateOutboxSmsStatus(sms.id, "SENT")
                if (sms.triggerHistoryId != null) {
                    repository.updateTriggerDeliveryStatus(sms.triggerHistoryId, "SMS_SENT")
                }
            } else {
                database.outboxSmsDao().incrementRetry(sms.id, "Network/Telephony error")
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
