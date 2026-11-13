package com.example.telephony

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarm.SosAlarmManager
import com.example.alarm.SosAlarmOverlayManager
import com.example.data.entity.CallEventEntity
import com.example.data.entity.ContactRuleEntity
import com.example.data.entity.TriggerHistoryEntity
import com.example.data.repository.SosRepository
import com.example.service.SosAlarmService
import com.example.sms.SmsDispatcher
import com.example.ui.alarm.SosAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CallStateTracker {
    private const val TAG = "CallStateTracker"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateMutex = Mutex()
    private val idleMutex = Mutex()
    private val processMutex = Mutex()

    @Volatile private var wasRinging: Boolean = false
    @Volatile private var wasOffhook: Boolean = false
    @Volatile private var lastRingingNumber: String? = null
    @Volatile private var lastRingingTimestamp: Long = 0L
    @Volatile private var lastSimSlot: Int = -1
    @Volatile private var lastHandledRingTimestamp: Long = 0L

    fun recordExternalRinging(timestamp: Long, incomingNumber: String?) {
        wasRinging = true
        wasOffhook = false
        if (lastRingingTimestamp == 0L || (timestamp - lastRingingTimestamp) > 60_000L) {
            lastRingingTimestamp = timestamp
        }
        if (!incomingNumber.isNullOrBlank() && incomingNumber != "UNKNOWN") {
            lastRingingNumber = incomingNumber
        }
    }

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
                var scheduleFollowUps = false
                stateMutex.withLock {
                    val isNewRing = !wasRinging && (lastRingingTimestamp == 0L || (now - lastRingingTimestamp) > 10_000L)
                    wasRinging = true
                    if (lastRingingTimestamp == 0L || (now - lastRingingTimestamp) > 60_000L) {
                        lastRingingTimestamp = now
                        repository.preferences.setLastRingingTimestamp(now)
                        scheduleFollowUps = true
                    } else if (isNewRing) {
                        scheduleFollowUps = true
                    }

                    if (!incomingNumber.isNullOrBlank() && incomingNumber != "UNKNOWN") {
                        lastRingingNumber = incomingNumber
                        repository.preferences.setLastRingingNumber(incomingNumber)
                    }
                    if (simSlot != -1) {
                        lastSimSlot = simSlot
                    }
                    wasOffhook = false
                    repository.preferences.setLastOffhookTimestamp(0L)
                }
                BackgroundMonitorScheduler.ensureMonitoringArmed(context)
                if (scheduleFollowUps && repository.preferences.isProtectionActive()) {
                    BackgroundMonitorScheduler.scheduleRingFollowUpChecks(context)
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)
                val answeredNum = stateMutex.withLock {
                    wasOffhook = true
                    wasRinging = false
                    lastRingingTimestamp = 0L
                    repository.preferences.setLastRingingTimestamp(0L)
                    repository.preferences.setLastOffhookTimestamp(now)
                    incomingNumber ?: lastRingingNumber ?: repository.preferences.getLastRingingNumber()
                }
                if (!answeredNum.isNullOrBlank() && answeredNum != "UNKNOWN") {
                    val norm = PhoneNumberHelper.normalizeToE164(answeredNum, defaultCountryIso)
                    val rule = repository.getRuleForNumber(norm) ?: repository.getRuleForNumber(answeredNum)
                    val canonicalNumber = rule?.normalizedNumber ?: norm
                    repository.markCallsAsHandled(canonicalNumber)
                }
                repository.markAllCallsBeforeAsHandled(now + 1000L)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                stateMutex.withLock {
                    if (!incomingNumber.isNullOrBlank() && incomingNumber != "UNKNOWN") {
                        lastRingingNumber = incomingNumber
                        repository.preferences.setLastRingingNumber(incomingNumber)
                    }
                }

                idleMutex.withLock {
                    data class IdleSnapshot(
                        val hadRing: Boolean,
                        val wasAnswered: Boolean,
                        val savedNumber: String?,
                        val savedRingTime: Long,
                        val savedSlot: Int
                    )

                    val snapshot = stateMutex.withLock {
                        val lastOffhook = repository.preferences.getLastOffhookTimestamp()
                        val answered = wasOffhook || (lastOffhook > 0L && (now - lastOffhook) < 5000L)
                        val prefRingTime = repository.preferences.getLastRingingTimestamp()
                        val ringStart = if (lastRingingTimestamp > 0L) lastRingingTimestamp else prefRingTime
                        val prefRingNum = repository.preferences.getLastRingingNumber()
                        val num = if (!incomingNumber.isNullOrBlank() && incomingNumber != "UNKNOWN") {
                            incomingNumber
                        } else {
                            lastRingingNumber ?: prefRingNum
                        }
                        val genuinelyRang = (wasRinging || (ringStart > 0L && (now - ringStart) < 120_000L)) &&
                                (ringStart == 0L || ringStart != lastHandledRingTimestamp)
                        val slot = lastSimSlot

                        if (genuinelyRang && ringStart > 0L) {
                            lastHandledRingTimestamp = ringStart
                        }

                        wasRinging = false
                        wasOffhook = false
                        lastRingingNumber = null
                        lastRingingTimestamp = 0L
                        lastSimSlot = -1
                        repository.preferences.setLastRingingTimestamp(0L)
                        repository.preferences.setLastRingingNumber(null)

                        IdleSnapshot(
                            hadRing = genuinelyRang,
                            wasAnswered = answered,
                            savedNumber = num,
                            savedRingTime = if (ringStart > 0L) ringStart else now,
                            savedSlot = slot
                        )
                    }

                    if (snapshot.wasAnswered) {
                        BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)
                        return@withLock
                    }

                    var processedFromLog = 0
                    if (CallLogReconciler.hasCallLogPermission(context)) {
                        val delaysMs = if (snapshot.hadRing) {
                            longArrayOf(400L, 650L, 850L, 1000L)
                        } else {
                            longArrayOf(350L, 700L)
                        }
                        for (waitMs in delaysMs) {
                            delay(waitMs)
                            processedFromLog = CallLogReconciler.reconcileMissedCalls(context, repository)
                            if (processedFromLog > 0) {
                                BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)
                                break
                            }
                        }
                    } else if (snapshot.hadRing) {
                        delay(300L)
                    }

                    if (processedFromLog == 0 && snapshot.hadRing) {
                        val alreadyRecorded = repository.hasAnyCallEventSince(snapshot.savedRingTime - 5000L)
                        if (!alreadyRecorded) {
                            val primaryRule = repository.getPrimaryActiveRule()
                            val effectiveRawNumber = if (!snapshot.savedNumber.isNullOrBlank() && snapshot.savedNumber != "UNKNOWN") {
                                snapshot.savedNumber
                            } else {
                                primaryRule?.normalizedNumber ?: "EMERGENCY_CALLER"
                            }
                            val effectiveNameHint = if (!snapshot.savedNumber.isNullOrBlank() && snapshot.savedNumber != "UNKNOWN") {
                                null
                            } else {
                                primaryRule?.displayName ?: "Emergency Caller"
                            }

                            val norm = if (effectiveRawNumber == "EMERGENCY_CALLER") {
                                "EMERGENCY_CALLER"
                            } else {
                                PhoneNumberHelper.normalizeToE164(effectiveRawNumber, defaultCountryIso)
                            }
                            val rule = repository.getRuleForNumber(norm) ?: repository.getRuleForNumber(effectiveRawNumber)
                            val canonicalNumber = rule?.normalizedNumber ?: norm

                            val recentEvents = repository.getUnhandledCallsSince(canonicalNumber, snapshot.savedRingTime - 2500L)
                            if (recentEvents.isEmpty()) {
                                processMissedCallInternal(
                                    context = context,
                                    repository = repository,
                                    rawNumber = effectiveRawNumber,
                                    timestamp = snapshot.savedRingTime,
                                    simSlot = snapshot.savedSlot,
                                    defaultCountryIso = defaultCountryIso,
                                    callerNameHint = effectiveNameHint,
                                    dedupToleranceMs = 800L
                                )
                                BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)
                            }
                        } else {
                            BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)
                        }
                    }

                    if (SosAlarmManager.isAlarmActive) {
                        delay(1500L)
                    }
                }
            }
        }
    }

    /**
     * Called by AlarmManager follow-up receiver (CallReconcileReceiver) after a RINGING event
     * in case OEM battery restrictions dropped the IDLE broadcast.
     */
    suspend fun reconcilePendingRingIfStale(context: Context, repository: SosRepository) {
        val now = System.currentTimeMillis()
        val ringTime = repository.preferences.getLastRingingTimestamp()
        val ringNum = repository.preferences.getLastRingingNumber()
        val lastOffhook = repository.preferences.getLastOffhookTimestamp()

        if (ringTime == 0L || (now - ringTime) > 120_000L || lastOffhook >= ringTime) {
            return
        }

        // Verify phone is actually back to IDLE before counting pending ring as missed
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val currentCallState = try {
                tm?.callState ?: TelephonyManager.CALL_STATE_IDLE
            } catch (_: Exception) {
                TelephonyManager.CALL_STATE_IDLE
            }
            if (currentCallState != TelephonyManager.CALL_STATE_IDLE) {
                return
            }
        }

        if (repository.hasAnyCallEventSince(ringTime - 5000L)) {
            repository.preferences.setLastRingingTimestamp(0L)
            repository.preferences.setLastRingingNumber(null)
            BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)
            return
        }

        val defaultCountryIso = PhoneNumberHelper.getDeviceCountryIso(context)
        val primaryRule = repository.getPrimaryActiveRule()
        val effectiveRawNumber = if (!ringNum.isNullOrBlank() && ringNum != "UNKNOWN") {
            ringNum
        } else {
            primaryRule?.normalizedNumber ?: "EMERGENCY_CALLER"
        }
        val effectiveNameHint = if (!ringNum.isNullOrBlank() && ringNum != "UNKNOWN") {
            null
        } else {
            primaryRule?.displayName ?: "Emergency Caller"
        }

        repository.preferences.setLastRingingTimestamp(0L)
        repository.preferences.setLastRingingNumber(null)
        BackgroundMonitorScheduler.cancelRingFollowUpChecks(context)

        processMissedCallInternal(
            context = context,
            repository = repository,
            rawNumber = effectiveRawNumber,
            timestamp = ringTime,
            simSlot = -1,
            defaultCountryIso = defaultCountryIso,
            callerNameHint = effectiveNameHint,
            dedupToleranceMs = 800L
        )
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
        isTest: Boolean = false,
        callerNameHint: String? = null,
        dedupToleranceMs: Long = 250L
    ) {
        processMutex.withLock {
            val normalizedNumber = PhoneNumberHelper.normalizeToE164(rawNumber, defaultCountryIso)

            val rule: ContactRuleEntity? = repository.getRuleForNumber(normalizedNumber)
                ?: repository.getRuleForNumber(rawNumber)

            val canonicalNumber = rule?.normalizedNumber ?: normalizedNumber
            val displayName = rule?.displayName?.takeIf { it.isNotBlank() }
                ?: callerNameHint?.takeIf { it.isNotBlank() }
                ?: rawNumber

            if (!isTest && repository.hasCallEvent(canonicalNumber, timestamp, toleranceMs = dedupToleranceMs)) {
                Log.d(TAG, "Call event already recorded in database: $canonicalNumber near $timestamp")
                return@withLock
            }

            val event = CallEventEntity(
                rawNumber = rawNumber,
                normalizedNumber = canonicalNumber,
                callerName = displayName,
                timestamp = timestamp,
                simSlot = simSlot,
                isHandled = false,
                callType = 3,
                isTest = isTest
            )
            repository.insertCallEvent(event)

            if (!isTest && !repository.preferences.isProtectionActive()) {
                Log.d(TAG, "Protection is paused or inactive; skipping trigger check")
                return@withLock
            }

            if (!isTest && rule != null && !rule.isAlarmEnabled) {
                Log.d(TAG, "Alarm explicitly disabled for contact rule ${rule.displayName}; skipping trigger")
                return@withLock
            }

            val windowMinutes = rule?.customWindowMinutes ?: repository.preferences.getGlobalWindowMinutes()
            val threshold = rule?.customThreshold ?: repository.preferences.getGlobalThreshold()

            val windowStart = System.currentTimeMillis() - (windowMinutes * 60_000L)
            val missedCount = repository.countUnhandledCallsSince(canonicalNumber, windowStart, isTest = isTest)
            Log.d(TAG, "Missed call counted: $missedCount / $threshold in window of ${windowMinutes}m for $displayName ($canonicalNumber)")

            if (missedCount >= threshold) {
                repository.markCallsAsHandled(canonicalNumber)
                if (rule != null) {
                    repository.updateLastTrigger(canonicalNumber, System.currentTimeMillis())
                }

                Log.d(TAG, "THRESHOLD REACHED ($missedCount / $threshold)! Triggering SOS Alarm for $displayName!")

                val trigger = TriggerHistoryEntity(
                    callerNumber = canonicalNumber,
                    callerName = displayName,
                    missedCount = missedCount,
                    triggeredAt = System.currentTimeMillis(),
                    deliveryStatus = "ALARM_FIRED",
                    isTest = isTest
                )
                val triggerId = repository.insertTrigger(trigger)

                startAlarmService(context, displayName, canonicalNumber, missedCount, repository)
                dispatchEmergencySms(context, repository, canonicalNumber, missedCount, triggerId, isTest = isTest)
            }
        }
    }

    fun startAlarmService(
        context: Context,
        displayName: String?,
        number: String,
        missedCount: Int,
        repository: SosRepository
    ) {
        val appContext = context.applicationContext
        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: number
        val tone = repository.preferences.getAlarmTone()
        val maxDuration = repository.preferences.getMaxAlarmDurationMinutes()
        val flashlight = repository.preferences.isFlashlightEnabled()
        val vibration = repository.preferences.isVibrationEnabled()

        // STEP 1 (t = 0ms, while Screen is STILL OFF / non-interactive):
        try {
            SosAlarmManager.startSosAlarm(
                context = appContext,
                callerName = resolvedName,
                callerNumber = number,
                missedCount = missedCount,
                tone = tone,
                enableFlashlight = flashlight,
                enableVibration = vibration,
                wakeScreenImmediately = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Direct startSosAlarm error: ${e.message}")
        }

        // STEP 2 (t = 0ms, while Screen is STILL OFF):
        // Post high-priority CATEGORY_ALARM notification with fullScreenIntent FIRST
        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(SosAlarmManager.NOTIFICATION_ID_ALARM)
            val notification = SosAlarmManager.buildAlarmNotification(
                context = appContext,
                callerName = resolvedName,
                callerNumber = number,
                missedCount = missedCount
            )
            nm?.notify(SosAlarmManager.NOTIFICATION_ID_ALARM, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Alarm notification post error: ${e.message}")
        }

        // STEP 3: Prepare SosAlarmService intent & Activity intent
        val serviceIntent = Intent(appContext, SosAlarmService::class.java).apply {
            action = SosAlarmService.ACTION_START_ALARM
            putExtra(SosAlarmService.EXTRA_CALLER_NAME, resolvedName)
            putExtra(SosAlarmService.EXTRA_CALLER_NUMBER, number)
            putExtra(SosAlarmService.EXTRA_MISSED_COUNT, missedCount)
            putExtra(SosAlarmService.EXTRA_TONE, tone)
            putExtra(SosAlarmService.EXTRA_MAX_DURATION, maxDuration)
            putExtra(SosAlarmService.EXTRA_FLASHLIGHT, flashlight)
            putExtra(SosAlarmService.EXTRA_VIBRATION, vibration)
        }

        val activityIntent = Intent(appContext, SosAlarmActivity::class.java).apply {
            setAction("com.example.action.SHOW_SOS_ALARM")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(SosAlarmActivity.EXTRA_CALLER_NAME, resolvedName)
            putExtra(SosAlarmActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(SosAlarmActivity.EXTRA_MISSED_COUNT, missedCount)
        }

        var foregroundServiceStarted = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
            foregroundServiceStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "Direct startForegroundService blocked/failed: ${e.message}")
        }

        try {
            appContext.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity fallback: ${e.message}")
        }

        // STEP 4: Schedule AlarmManager.setAlarmClock (system_server exemption for BOTH FGS + Lockscreen Activity!)
        try {
            val creatorOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    android.app.ActivityOptions.makeBasic().apply {
                        setPendingIntentCreatorBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }.toBundle()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val pendingActivity = PendingIntent.getActivity(
                appContext,
                1005,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions
            )
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())) {
                // If direct startForegroundService was blocked (Android 12-16 JobService/Receiver FGS restriction),
                // use setAlarmClock to fire PendingIntent.getForegroundService with ALARM_CLOCK FGS exemption!
                if (!foregroundServiceStarted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pendingService = PendingIntent.getForegroundService(
                        appContext,
                        1006,
                        serviceIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 60L, pendingActivity),
                        pendingService
                    )
                }

                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 120L, pendingActivity),
                    pendingActivity
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AlarmClock launch fallback error: ${e.message}")
        }

        // STEP 5 (t = 140ms & t = 700ms): Wake screen hardware & re-ensure Overlay + Activity
        scope.launch {
            delay(140L)
            if (SosAlarmManager.isAlarmActive) {
                SosAlarmManager.wakeUpScreen(appContext)
                SosAlarmOverlayManager.show(
                    context = appContext,
                    callerName = resolvedName,
                    callerNumber = number,
                    missedCount = missedCount
                )
                try {
                    val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.appTasks?.forEach { task ->
                        try { task.moveToFront() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                try {
                    appContext.startActivity(activityIntent)
                } catch (_: Exception) {}
            }

            delay(600L)
            if (SosAlarmManager.isAlarmActive) {
                SosAlarmManager.wakeUpScreen(appContext)
                SosAlarmOverlayManager.show(
                    context = appContext,
                    callerName = resolvedName,
                    callerNumber = number,
                    missedCount = missedCount
                )
                try {
                    appContext.startActivity(activityIntent)
                } catch (_: Exception) {}
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
