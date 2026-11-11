package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.alarm.SosAlarmManager
import com.example.alarm.SosAlarmOverlayManager
import com.example.ui.alarm.SosAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SosAlarmService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var autoStopJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_ALARM) {
            SosAlarmManager.stopSosAlarm(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME)
        val callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Unknown"
        val missedCount = intent?.getIntExtra(EXTRA_MISSED_COUNT, 5) ?: 5
        val tone = intent?.getStringExtra(EXTRA_TONE) ?: "system_alarm"
        val maxDurationMinutes = intent?.getIntExtra(EXTRA_MAX_DURATION, 3) ?: 3
        val flashlight = intent?.getBooleanExtra(EXTRA_FLASHLIGHT, true) ?: true
        val vibration = intent?.getBooleanExtra(EXTRA_VIBRATION, true) ?: true

        // 1. Build & show high-priority full-screen alarm notification via startForeground ONLY
        // (Do NOT call nm.notify with the same ID immediately after startForeground, as Android treats
        // duplicate posts as updates and suppresses fullScreenIntent!)
        val notification = SosAlarmManager.buildAlarmNotification(
            context = this,
            callerName = callerName,
            callerNumber = callerNumber,
            missedCount = missedCount
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SosAlarmManager.NOTIFICATION_ID_ALARM,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(SosAlarmManager.NOTIFICATION_ID_ALARM, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("SosAlarmService", "startForeground failed: ${e.message}")
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.notify(SosAlarmManager.NOTIFICATION_ID_ALARM, notification)
            } catch (_: Exception) {}
        }

        // 2. Start sound, vibration, flashlight, wake lock, StateFlow update, AND System Overlay Window
        SosAlarmManager.startSosAlarm(
            context = this,
            callerName = callerName,
            callerNumber = callerNumber,
            missedCount = missedCount,
            tone = tone,
            enableFlashlight = flashlight,
            enableVibration = vibration
        )

        // 3. Also launch full-screen SosAlarmActivity (for lockscreen and devices without overlay permission)
        val alarmIntent = Intent(this, SosAlarmActivity::class.java).apply {
            setAction("com.example.action.SHOW_SOS_ALARM")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(SosAlarmActivity.EXTRA_CALLER_NAME, callerName ?: callerNumber)
            putExtra(SosAlarmActivity.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(SosAlarmActivity.EXTRA_MISSED_COUNT, missedCount)
        }

        // Direct startActivity
        try {
            startActivity(alarmIntent)
        } catch (e: Exception) {
            android.util.Log.e("SosAlarmService", "Direct startActivity: ${e.message}")
        }

        // PendingIntent send with Android 14+ (API 34+) background activity launch mode
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

        val senderOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                android.app.ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            creatorOptions
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && senderOptions != null) {
                fullScreenPendingIntent.send(this, 0, null, null, null, null, senderOptions)
            } else {
                fullScreenPendingIntent.send()
            }
        } catch (e: Exception) {
            android.util.Log.e("SosAlarmService", "PendingIntent send: ${e.message}")
        }

        // 4. Auto-stop timer
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(maxDurationMinutes * 60_000L)
            SosAlarmManager.stopSosAlarm(this@SosAlarmService)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        autoStopJob?.cancel()
        SosAlarmManager.stopSosAlarm(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_ALARM = "com.example.service.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.service.action.STOP_ALARM"

        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_MISSED_COUNT = "extra_missed_count"
        const val EXTRA_TONE = "extra_tone"
        const val EXTRA_MAX_DURATION = "extra_max_duration"
        const val EXTRA_FLASHLIGHT = "extra_flashlight"
        const val EXTRA_VIBRATION = "extra_vibration"

        fun stop(context: Context) {
            val appContext = context.applicationContext
            // Immediately stop sound, vibration, flashlight, overlay, and reset StateFlow
            SosAlarmManager.stopSosAlarm(appContext)
            SosAlarmOverlayManager.dismiss()
            try {
                val intent = Intent(appContext, SosAlarmService::class.java).apply {
                    action = ACTION_STOP_ALARM
                }
                appContext.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
