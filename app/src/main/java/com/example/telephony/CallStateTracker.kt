package com.example.telephony

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.entity.CallEventEntity
import com.example.data.entity.ContactRuleEntity
import com.example.data.entity.TriggerHistoryEntity
import com.example.data.repository.SosRepository
import com.example.service.SosAlarmService
import com.example.sms.SmsDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CallStateTracker {
    private const val TAG = "CallStateTracker"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var wasRinging: Boolean = false
    private var wasOffhook: Boolean = false
    private var lastRingingNumber: String? = null
    private var lastRingingTimestamp: Long = 0L
    private var lastSimSlot: Int = -1

    private var lastProcessedCanonicalNumber: String? = null
    private var lastProcessedTimestamp: Long = 0L

    fun onCallStateChanged(
        context: Context,
        repository: SosRepository,
        state: Int,
        incomingNumber: String?,
        simSlot: Int = -1
    ) {
        scope.launch {
            handlePhoneStateBroadcast(
                context = context,
                repository = repository,
                state = state,
                incomingNumber = incomingNumber,
                simSlot = simSlot
            )
        }
    }

    suspend fun handlePhoneStateBroadcast(
        context: Context,
        repository: SosRepository,
        state: Int,
        incomingNumber: String?,
        simSlot: Int = -1
    ) {
        val defaultCountryIso = PhoneNumberHelper.getDeviceCountryIso(context)
        val now = System.currentTimeMillis()

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                wasRinging = true
                lastRingingTimestamp = now
                repository.preferences.setLastRingingTimestamp(now)

                if (!incomingNumber.isNullOrBlank() && incomingNumber != "UNKNOWN") {
                    lastRingingNumber = incomingNumber
                    repository.preferences.setLastRingingNumber(incomingNumber)
                }
                lastSimSlot = simSlot
                wasOffhook = false
                repository.preferences.setLastOffhookTimestamp(0L)
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call was answered or outgoing call started
                wasOffhook = true
                repository.preferences.setLastOffhookTimestamp(now)

                val answeredNum = incomingNumber ?: lastRingingNumber ?: repository.preferences.getLastRingingNumber()
                if (!answeredNum.isNullOrBlank()) {
                    val norm = PhoneNumberHelper.normalizeToE164(answeredNum, defaultCountryIso)
                    repository.markCallsAsHandled(norm)
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                val lastOffhook = repository.preferences.getLastOffhookTimestamp()
                val wasAnswered = wasOffhook || (now - lastOffhook < 4000L)

                if (wasAnswered) {
                    // Call was answered or completed - reset states and don't count as missed
                    wasRinging = false
                    wasOffhook = false
                    lastRingingNumber = null
                    lastRingingTimestamp = 0L
                    repository.preferences.setLastRingingTimestamp(0L)
                    repository.preferences.setLastRingingNumber(null)
                    return
                }

                // Call was NOT answered - query CallLog to identify the exact missed caller
                val prefRingTime = repository.preferences.getLastRingingTimestamp()
                val ringStart = if (lastRingingTimestamp > 0) lastRingingTimestamp else prefRingTime
                val minCallDate = if (ringStart > 0) ringStart - 5000L else (now - 120_000L)

                var foundNumber: String? = null
                var foundTimestamp: Long = now

                // Poll CallLog with progressive delays for this NEW call (ignoring any older already-processed calls)
                for (attempt in 1..8) {
                    delay(400L)
                    val fromLog = CallLogReconciler.getLatestUnprocessedMissedCall(
                        context = context,
                        repository = repository,
                        minDate = minCallDate
                    )
                    if (fromLog != null) {
                        foundNumber = fromLog.first
                        foundTimestamp = fromLog.second
                        break
                    }
                }

                // Fallback: If CallLog is delayed or inaccessible, use the immediate number captured during ringing
                if (foundNumber.isNullOrBlank()) {
                    val prefRingNum = repository.preferences.getLastRingingNumber()
                    val immediateNum = if (!incomingNumber.isNullOrBlank() && incomingNumber != "UNKNOWN") {
                        incomingNumber
                    } else {
                        lastRingingNumber ?: prefRingNum
                    }
                    if (!immediateNum.isNullOrBlank() && immediateNum != "UNKNOWN") {
                        foundNumber = immediateNum
                        foundTimestamp = if (ringStart > 0) ringStart else now
                    }
                }

                // Reset ringing state for next call
                val slot = lastSimSlot
                wasRinging = false
                wasOffhook = false
                lastRingingNumber = null
                lastRingingTimestamp = 0L
                lastSimSlot = -1
                repository.preferences.setLastRingingTimestamp(0L)
                repository.preferences.setLastRingingNumber(null)

                if (!foundNumber.isNullOrBlank()) {
                    processMissedCallInternal(
                        context = context,
                        repository = repository,
                        rawNumber = foundNumber,
                        timestamp = foundTimestamp,
                        simSlot = slot,
                        defaultCountryIso = defaultCountryIso
                    )
                }
            }
        }
    }

    fun processMissedCall(
        context: Context,
        repository: SosRepository,
        rawNumber: String,
        timestamp: Long,
        simSlot: Int,
        defaultCountryIso: String = "IN",
        isTest: Boolean = false
    ) {
        scope.launch {
            processMissedCallInternal(
                context = context,
                repository = repository,
                rawNumber = rawNumber,
                timestamp = timestamp,
                simSlot = simSlot,
                defaultCountryIso = defaultCountryIso,
                isTest = isTest
            )
        }
    }

    suspend fun processMissedCallInternal(
        context: Context,
        repository: SosRepository,
        rawNumber: String,
        timestamp: Long,
        simSlot: Int,
        defaultCountryIso: String = "IN",
        isTest: Boolean = false
    ) {
            val normalizedNumber = PhoneNumberHelper.normalizeToE164(rawNumber, defaultCountryIso)

            // Look up contact rule (checks exact, normalized, and last 10-digits suffix)
            val rule: ContactRuleEntity? = repository.getRuleForNumber(normalizedNumber)
                ?: repository.getRuleForNumber(rawNumber)

            // CRITICAL: Always use rule.normalizedNumber as the canonical key so counts are 100% consistent across formats!
            val canonicalNumber = rule?.normalizedNumber ?: normalizedNumber

            // Debounce duplicate events for same canonical number within 3000ms (prevents double count if both CallLogObserver and PhoneStateReceiver detect it)
            val isDuplicate = synchronized(this@CallStateTracker) {
                val now = System.currentTimeMillis()
                if (!isTest && canonicalNumber == lastProcessedCanonicalNumber && (now - lastProcessedTimestamp) < 3000L) {
                    Log.d(TAG, "Duplicate missed call broadcast ignored for $canonicalNumber")
                    true
                } else {
                    lastProcessedCanonicalNumber = canonicalNumber
                    lastProcessedTimestamp = now
                    false
                }
            }
            if (isDuplicate) return

            // Database-level deduplication: Prevent double-processing identical call events
            if (!isTest && repository.hasCallEvent(canonicalNumber, timestamp)) {
                Log.d(TAG, "Call event already recorded in database: $canonicalNumber at $timestamp")
                return
            }

            // Persist call event in Room with isTest flag
            val event = CallEventEntity(
                rawNumber = rawNumber,
                normalizedNumber = canonicalNumber,
                callerName = rule?.displayName,
                timestamp = timestamp,
                simSlot = simSlot,
                isHandled = false,
                callType = 3, // MISSED
                isTest = isTest
            )
            repository.insertCallEvent(event)

            // If protection is paused or disabled, do not trigger (unless in test mode)
            if (!isTest && !repository.preferences.isProtectionActive()) {
                Log.d(TAG, "Protection is paused or inactive; skipping trigger check")
                return
            }

            // Unknown number check: default is OFF unless contact is added
            if (rule == null) {
                Log.d(TAG, "Call from $rawNumber ($canonicalNumber) does not match any emergency contact rule")
                return
            }

            // Determine sliding window parameters
            val windowMinutes = rule.customWindowMinutes ?: repository.preferences.getGlobalWindowMinutes()
            val threshold = rule.customThreshold ?: repository.preferences.getGlobalThreshold()
            val cooldownMinutes = repository.preferences.getCooldownMinutes()

            val windowStart = System.currentTimeMillis() - (windowMinutes * 60_000L)
            val missedCount = repository.countUnhandledCallsSince(canonicalNumber, windowStart, isTest = isTest)
            Log.d(TAG, "Missed call counted: $missedCount / $threshold in window of ${windowMinutes}m for ${rule.displayName} ($canonicalNumber)")

            // Check if threshold is reached
            if (missedCount >= threshold) {
                // Check cooldown (skip if isTest)
                if (!isTest) {
                    val cooldownMillis = cooldownMinutes * 60_000L
                    val lastTrigger = rule.lastTriggerTimestamp
                    if (System.currentTimeMillis() - lastTrigger < cooldownMillis) {
                        Log.d(TAG, "Cooldown active (${cooldownMinutes}m); skipping alarm")
                        return
                    }
                    repository.updateLastTrigger(canonicalNumber, System.currentTimeMillis())
                }

                Log.d(TAG, "THRESHOLD REACHED! Triggering SOS Alarm for ${rule.displayName}!")

                // Record trigger history
                val trigger = TriggerHistoryEntity(
                    callerNumber = canonicalNumber,
                    callerName = rule.displayName,
                    missedCount = missedCount,
                    triggeredAt = System.currentTimeMillis(),
                    deliveryStatus = "ALARM_FIRED",
                    isTest = isTest
                )
                val triggerId = repository.insertTrigger(trigger)

                // 1. Fire SOS Alarm if Toggle A is enabled
                if (rule.isAlarmEnabled) {
                    startAlarmService(context, rule.displayName, canonicalNumber, missedCount, repository)
                }

                // 2. Dispatch Emergency Alert SMS (offline emergency SMS to caller)
                dispatchEmergencySms(context, repository, canonicalNumber, missedCount, triggerId, isTest = isTest)
            }
    }

    private fun startAlarmService(
        context: Context,
        displayName: String?,
        number: String,
        missedCount: Int,
        repository: SosRepository
    ) {
        val intent = Intent(context, SosAlarmService::class.java).apply {
            action = SosAlarmService.ACTION_START_ALARM
            putExtra(SosAlarmService.EXTRA_CALLER_NAME, displayName ?: number)
            putExtra(SosAlarmService.EXTRA_CALLER_NUMBER, number)
            putExtra(SosAlarmService.EXTRA_MISSED_COUNT, missedCount)
            putExtra(SosAlarmService.EXTRA_TONE, repository.preferences.getAlarmTone())
            putExtra(SosAlarmService.EXTRA_MAX_DURATION, repository.preferences.getMaxAlarmDurationMinutes())
            putExtra(SosAlarmService.EXTRA_FLASHLIGHT, repository.preferences.isFlashlightEnabled())
            putExtra(SosAlarmService.EXTRA_VIBRATION, repository.preferences.isVibrationEnabled())
        }

        // Immediately show the System Overlay Window if SYSTEM_ALERT_WINDOW is granted
        com.example.alarm.SosAlarmOverlayManager.show(
            context = context,
            callerName = displayName ?: number,
            callerNumber = number,
            missedCount = missedCount
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SosAlarmService: ${e.message}, executing direct emergency alarm fallback")
            try {
                // Direct emergency playback + StateFlow + Overlay fallback
                com.example.alarm.SosAlarmManager.startSosAlarm(
                    context = context,
                    callerName = displayName ?: number,
                    callerNumber = number,
                    missedCount = missedCount,
                    tone = repository.preferences.getAlarmTone(),
                    enableFlashlight = repository.preferences.isFlashlightEnabled(),
                    enableVibration = repository.preferences.isVibrationEnabled()
                )
                // Post full-screen alarm notification
                val notification = com.example.alarm.SosAlarmManager.buildAlarmNotification(
                    context = context,
                    callerName = displayName ?: number,
                    callerNumber = number,
                    missedCount = missedCount
                )
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.notify(com.example.alarm.SosAlarmManager.NOTIFICATION_ID_ALARM, notification)

                // Direct Activity launch
                val activityIntent = Intent(context, com.example.ui.alarm.SosAlarmActivity::class.java).apply {
                    setAction("com.example.action.SHOW_SOS_ALARM")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                    putExtra(com.example.ui.alarm.SosAlarmActivity.EXTRA_CALLER_NAME, displayName ?: number)
                    putExtra(com.example.ui.alarm.SosAlarmActivity.EXTRA_CALLER_NUMBER, number)
                    putExtra(com.example.ui.alarm.SosAlarmActivity.EXTRA_MISSED_COUNT, missedCount)
                }
                context.startActivity(activityIntent)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Direct alarm fallback failed: ${fallbackError.message}")
            }
        }
    }

    private fun dispatchEmergencySms(
        context: Context,
        repository: SosRepository,
        recipientNumber: String,
        missedCount: Int,
        triggerId: Long,
        isTest: Boolean = false
    ) {
        scope.launch {
            // Queue & send SMS via SmsDispatcher (pure offline text alert, no location)
            SmsDispatcher.queueEmergencySms(
                context = context,
                repository = repository,
                recipientNumber = recipientNumber,
                missedCount = missedCount,
                triggerHistoryId = triggerId,
                isTest = isTest
            )
        }
    }
}
