package com.example.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.MissedCallSosApp
import com.example.alarm.SosAlarmManager
import com.example.alarm.SosAlarmOverlayManager
import com.example.service.SosAlarmService
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.GlassTier

class SosAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val params = window.attributes
        params.screenBrightness = 1.0f
        window.attributes = params

        // Since SosAlarmActivity is now visibly on screen, dismiss the duplicate system overlay if present
        SosAlarmOverlayManager.dismiss()

        val initialName = intent?.getStringExtra(EXTRA_CALLER_NAME)
            ?: SosAlarmManager.activeCallerName
            ?: "Emergency Contact"
        val initialNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER)
            ?: SosAlarmManager.activeCallerNumber.ifBlank { "Unknown" }
        val initialCount = intent?.getIntExtra(EXTRA_MISSED_COUNT, SosAlarmManager.activeMissedCount.takeIf { it > 0 } ?: 5)
            ?: 5

        val app = application as MissedCallSosApp
        val preferences = app.preferences
        val pinRequired = preferences.isPinRequired()
        val correctPin = preferences.getAlarmPin()

        setContent {
            val alarmState by SosAlarmManager.alarmStateFlow.collectAsState()

            val displayCallerName = if (alarmState.callerName.isNotBlank()) alarmState.callerName else initialName
            val displayCallerNumber = if (alarmState.callerNumber.isNotBlank()) alarmState.callerNumber else initialNumber
            val displayMissedCount = if (alarmState.missedCount > 0) alarmState.missedCount else initialCount

            GlassTheme(tierOverride = GlassTier.TIER_C, darkTheme = true) {
                BackHandler {
                    // Prevent accidental back button exit while alarm is active
                }

                SosAlarmContent(
                    callerName = displayCallerName,
                    callerNumber = displayCallerNumber,
                    missedCount = displayMissedCount,
                    pinRequired = pinRequired,
                    correctPin = correctPin,
                    onStopAlarm = {
                        SosAlarmService.stop(this@SosAlarmActivity)
                        finishAndRemoveTask()
                    },
                    onCallBack = {
                        SosAlarmService.stop(this@SosAlarmActivity)
                        try {
                            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$displayCallerNumber")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(callIntent)
                        } catch (_: Exception) {}
                        finishAndRemoveTask()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SosAlarmOverlayManager.dismiss()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    override fun onResume() {
        super.onResume()
        SosAlarmOverlayManager.dismiss()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_MISSED_COUNT = "extra_missed_count"
    }
}
