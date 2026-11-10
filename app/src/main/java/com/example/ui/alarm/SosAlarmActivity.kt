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
import androidx.compose.ui.Modifier
import com.example.MissedCallSosApp
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
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val params = window.attributes
        params.screenBrightness = 1.0f
        window.attributes = params

        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "Emergency Contact"
        val callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Unknown"
        val missedCount = intent?.getIntExtra(EXTRA_MISSED_COUNT, 5) ?: 5

        val app = application as MissedCallSosApp
        val preferences = app.preferences
        val pinRequired = preferences.isPinRequired()
        val correctPin = preferences.getAlarmPin()

        setContent {
            // SOS Alarm Screen always enforces Tier C (safe, non-blur, high contrast)
            GlassTheme(tierOverride = GlassTier.TIER_C, darkTheme = true) {
                BackHandler {
                    // Prevent accidental back exit from bypassing alarm
                }

                SosAlarmContent(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    missedCount = missedCount,
                    pinRequired = pinRequired,
                    correctPin = correctPin,
                    onStopAlarm = {
                        SosAlarmService.stop(this)
                        finish()
                    },
                    onCallBack = {
                        SosAlarmService.stop(this)
                        val callIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$callerNumber")
                        }
                        startActivity(callIntent)
                        finish()
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
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    override fun onResume() {
        super.onResume()
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
