package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.alarm.SosAlarmManager
import com.example.alarm.SosAlarmOverlayManager
import com.example.service.SosAlarmService
import com.example.ui.alarm.SosAlarmContent
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.NavigationTab
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.HealthCheckScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.OpenSourceLicensesScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TestModeScreen
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.ProtectionVisualStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MissedCallSosApp
        val repository = app.repository
        val preferences = app.preferences

        if (preferences.isOnboardingCompleted() && preferences.isProtectionActive()) {
            com.example.service.CallDetectionService.start(this)
            com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(this)
        }

        setContent {
            val alarmState by SosAlarmManager.alarmStateFlow.collectAsState()

            val visualQuality by preferences.visualQualityFlow.collectAsState()
            val reduceTransparencyPref by preferences.reduceTransparencyFlow.collectAsState()
            val reduceMotionPref by preferences.reduceMotionFlow.collectAsState()
            val batterySaverMode by preferences.batterySaverFlow.collectAsState()

            val pm = remember { getSystemService(android.os.PowerManager::class.java) }
            val isOsPowerSave = pm?.isPowerSaveMode == true
            val isBatteryOptimized = batterySaverMode || isOsPowerSave

            val reduceTransparency = reduceTransparencyPref || isBatteryOptimized
            val reduceMotion = reduceMotionPref || isBatteryOptimized

            val tier = remember(visualQuality, reduceTransparency, isBatteryOptimized) {
                if (isBatteryOptimized) {
                    com.example.ui.theme.GlassTier.TIER_C
                } else {
                    DesignTokens.detectTier(this, visualQuality, reduceTransparency)
                }
            }

            var isOnboardingCompleted by remember {
                mutableStateOf(preferences.isOnboardingCompleted())
            }

            var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
            var isTestingMode by remember { mutableStateOf(false) }
            var isAboutMode by remember { mutableStateOf(false) }
            var isLicensesMode by remember { mutableStateOf(false) }

            GlassTheme(
                tierOverride = tier,
                reduceTransparency = reduceTransparency,
                reduceMotion = reduceMotion,
                darkTheme = true
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isOnboardingCompleted) {
                        OnboardingScreen(
                            onComplete = {
                                preferences.setOnboardingCompleted(true)
                                isOnboardingCompleted = true
                                if (preferences.isProtectionActive()) {
                                    com.example.service.CallDetectionService.start(this@MainActivity)
                                    com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(this@MainActivity)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val corePermissions = remember {
                            buildList {
                                add(android.Manifest.permission.READ_PHONE_STATE)
                                add(android.Manifest.permission.READ_CALL_LOG)
                                add(android.Manifest.permission.READ_CONTACTS)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                        }

                        val corePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                        ) {
                            if (preferences.isProtectionActive()) {
                                com.example.service.CallDetectionService.start(this@MainActivity)
                                com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(this@MainActivity)
                            }
                        }

                        LaunchedEffect(Unit) {
                            val missingCore = corePermissions.any { perm ->
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    perm
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                            if (missingCore) {
                                corePermissionLauncher.launch(corePermissions)
                            }
                        }

                        BackHandler(enabled = !alarmState.isActive && (isLicensesMode || isAboutMode || isTestingMode || currentTab != NavigationTab.HOME)) {
                            when {
                                isLicensesMode -> isLicensesMode = false
                                isAboutMode -> isAboutMode = false
                                isTestingMode -> isTestingMode = false
                                else -> currentTab = NavigationTab.HOME
                            }
                        }

                        var showOverlayPrompt by remember {
                            mutableStateOf(!Settings.canDrawOverlays(this@MainActivity))
                        }

                        if (showOverlayPrompt && !Settings.canDrawOverlays(this@MainActivity) && !alarmState.isActive) {
                            AlertDialog(
                                onDismissRequest = { showOverlayPrompt = false },
                                containerColor = Color(0xFF131B33),
                                title = {
                                    Text(
                                        text = "Lockscreen Alarm Display Permission",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text(
                                        text = "To pop up the emergency red alarm screen over your locked screen and active apps, please allow 'Display Over Other Apps' (Appear on top).",
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                },
                                confirmButton = {
                                    GlassButton(
                                        onClick = {
                                            showOverlayPrompt = false
                                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                data = Uri.parse("package:$packageName")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            try {
                                                startActivity(intent)
                                            } catch (_: Exception) {
                                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:$packageName")
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                })
                                            }
                                        },
                                        variant = GlassButtonVariant.PRIMARY
                                    ) {
                                        Text("Enable Now", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    GlassButton(
                                        onClick = { showOverlayPrompt = false },
                                        variant = GlassButtonVariant.SECONDARY
                                    ) {
                                        Text("Later")
                                    }
                                }
                            )
                        }

                        val pausedUntil by preferences.pausedUntilFlow.collectAsState()
                        val isPaused = pausedUntil > System.currentTimeMillis()
                        val backgroundStatus = if (isPaused) ProtectionVisualStatus.ATTENTION else ProtectionVisualStatus.ACTIVE

                        GlassBackground(status = backgroundStatus) {
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    if (!isTestingMode && !isAboutMode && !isLicensesMode) {
                                        GlassBottomBar(
                                            currentTab = currentTab,
                                            onTabSelected = {
                                                isAboutMode = false
                                                isLicensesMode = false
                                                currentTab = it
                                            }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when {
                                        isLicensesMode -> {
                                            OpenSourceLicensesScreen(
                                                onNavigateBack = { isLicensesMode = false },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        isAboutMode -> {
                                            AboutScreen(
                                                onNavigateBack = { isAboutMode = false },
                                                onNavigateToLicenses = { isLicensesMode = true },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        isTestingMode -> {
                                            TestModeScreen(
                                                repository = repository,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        currentTab == NavigationTab.HOME -> {
                                            HomeScreen(
                                                repository = repository,
                                                onNavigateToHealth = { currentTab = NavigationTab.HEALTH },
                                                onNavigateToTest = { isTestingMode = true },
                                                onNavigateToAbout = { isAboutMode = true },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        currentTab == NavigationTab.RULES -> {
                                            RulesScreen(
                                                repository = repository,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        currentTab == NavigationTab.HISTORY -> {
                                            HistoryScreen(
                                                repository = repository,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        currentTab == NavigationTab.HEALTH -> {
                                            HealthCheckScreen(
                                                repository = repository,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        currentTab == NavigationTab.SETTINGS -> {
                                            SettingsScreen(
                                                repository = repository,
                                                onNavigateToAbout = { isAboutMode = true },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Guaranteed Full-Screen Reactive Alarm Dismissal Screen inside MainActivity
                    if (alarmState.isActive) {
                        BackHandler {
                            // Prevent accidental back press while alarm is sounding
                        }
                        SosAlarmContent(
                            callerName = alarmState.callerName.ifBlank { "Emergency Contact" },
                            callerNumber = alarmState.callerNumber.ifBlank { "Unknown" },
                            missedCount = if (alarmState.missedCount > 0) alarmState.missedCount else 5,
                            pinRequired = preferences.isPinRequired(),
                            correctPin = preferences.getAlarmPin(),
                            onStopAlarm = {
                                SosAlarmService.stop(this@MainActivity)
                            },
                            onCallBack = {
                                val num = alarmState.callerNumber
                                SosAlarmService.stop(this@MainActivity)
                                if (num.isNotBlank()) {
                                    try {
                                        val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$num")
                                        }
                                        startActivity(callIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as MissedCallSosApp
        if (app.preferences.isOnboardingCompleted() && app.preferences.isProtectionActive()) {
            com.example.service.CallDetectionService.start(this)
            com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(this)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.telephony.CallLogReconciler.reconcileMissedCalls(this@MainActivity, app.repository)
            }
        }
    }
}
