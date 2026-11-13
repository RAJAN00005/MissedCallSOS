package com.example.service

import android.app.ActivityManager
import android.app.NotificationManager
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

        // 1. Start CPU wake lock, sound, vibration, flashlight, and overlay FIRST (without waking display yet so fullScreenIntent fires!)
        SosAlarmManager.startSosAlarm(
            context = this,
            callerName = callerName,
            callerNumber = callerNumber,
            missedCount = missedCount,
            tone = tone,
            enableFlashlight = flashlight,
            enableVibration = vibration,
            wakeScreenImmediately = false
        )

        // 2. Build & post high-priority full-screen alarm notification while screen is non-interactive
        val notification = SosAlarmManager.buildAlarmNotification(
            context = this,
            callerName = callerName,
            callerNumber = callerNumber,
            missedCount = missedCount
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        SosAlarmManager.NOTIFICATION_ID_ALARM,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } catch (_: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            SosAlarmManager.NOTIFICATION_ID_ALARM,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(SosAlarmManager.NOTIFICATION_ID_ALARM, notification)
                    }
                }
            } else {
                startForeground(SosAlarmManager.NOTIFICATION_ID_ALARM, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("SosAlarmService", "startForeground fallback: ${e.message}")
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(SosAlarmManager.NOTIFICATION_ID_ALARM, notification)
            } catch (_: Exception) {}
        }

        // 3. Bring existing app task to front if running in recents
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task ->
                try {
                    task.moveToFront()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 4. Launch standalone SosAlarmActivity & wake up screen
        val alarmIntent = Intent(this, SosAlarmActivity::class.java).apply {
            setAction("com.example.action.SHOW_SOS_ALARM")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(SosAlarmActivity.EXTRA_CALLER_NAME, callerName ?: callerNumber)
            putExtra(SosAlarmActivity.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(SosAlarmActivity.EXTRA_MISSED_COUNT, missedCount)
        }

        try {
            startActivity(alarmIntent)
        } catch (_: Exception) {}

        scope.launch {
            delay(120L)
            if (SosAlarmManager.isAlarmActive) {
                SosAlarmManager.wakeUpScreen(this@SosAlarmService)
                SosAlarmOverlayManager.show(
                    context = this@SosAlarmService,
                    callerName = callerName ?: callerNumber,
                    callerNumber = callerNumber,
                    missedCount = missedCount
                )
                try {
                    startActivity(alarmIntent)
                } catch (_: Exception) {}
            }
        }

        // 5. Auto-stop timer
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
            SosAlarmManager.stopSosAlarm(appContext)
            SosAlarmOverlayManager.dismiss()
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(SosAlarmManager.NOTIFICATION_ID_ALARM)
                nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)
            } catch (_: Exception) {}
            try {
                val intent = Intent(appContext, SosAlarmService::class.java).apply {
                    action = ACTION_STOP_ALARM
                }
                appContext.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
