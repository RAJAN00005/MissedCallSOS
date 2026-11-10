package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.data.entity.ContactRuleEntity
import com.example.telephony.PhoneNumberHelper
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.NavigationTab
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.DesignGalleryScreen
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MissedCallSosApp
        val repository = app.repository
        val preferences = app.preferences

        if (preferences.isOnboardingCompleted() && preferences.isProtectionActive()) {
            com.example.service.CallDetectionService.start(this)
        }
        checkActiveAlarmAndRedirect()

        setContent {
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
                if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        onComplete = {
                            preferences.setOnboardingCompleted(true)
                            isOnboardingCompleted = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BackHandler(enabled = isLicensesMode || isAboutMode || isTestingMode || currentTab != NavigationTab.HOME) {
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

                    if (showOverlayPrompt && !Settings.canDrawOverlays(this@MainActivity)) {
                        AlertDialog(
                            onDismissRequest = { showOverlayPrompt = false },
                            containerColor = Color(0xFF131B33),
                            title = {
                                Text(
                                    text = "स्क्रीन पर अलार्म बटन के लिए अनुमति",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = "मिस कॉल आने पर अलार्म बंद करने की लाल स्क्रीन स्क्रीन पर सबसे ऊपर अपने आप आने के लिए 'Display Over Other Apps' (या Appear on top) को Allow कर दें।\n\n(Xiaomi / Realme / Oppo यूज़र्स 'Show on lockscreen' भी Allow करें)",
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            },
                            confirmButton = {
                                GlassButton(
                                    onClick = {
                                        showOverlayPrompt = false
                                        val manufacturer = android.os.Build.MANUFACTURER.uppercase()
                                        var handled = false
                                        if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO")) {
                                            try {
                                                val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                                                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                                    putExtra("extra_pkgname", packageName)
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                startActivity(miuiIntent)
                                                handled = true
                                            } catch (_: Exception) {}
                                        }
                                        if (!handled) {
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
                                        }
                                    },
                                    variant = GlassButtonVariant.PRIMARY
                                ) {
                                    Text("अभी चालू करें (Enable Now)", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                GlassButton(
                                    onClick = { showOverlayPrompt = false },
                                    variant = GlassButtonVariant.SECONDARY
                                ) {
                                    Text("बाद में (Later)")
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkActiveAlarmAndRedirect()
        val app = application as MissedCallSosApp
        if (app.preferences.isOnboardingCompleted() && app.preferences.isProtectionActive()) {
            com.example.service.CallDetectionService.start(this)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.telephony.CallLogReconciler.reconcileMissedCalls(this@MainActivity, app.repository)
            }
        }
    }

    private fun checkActiveAlarmAndRedirect() {
        if (com.example.alarm.SosAlarmManager.isAlarmActive) {
            val alarmIntent = android.content.Intent(this, com.example.ui.alarm.SosAlarmActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(
                    com.example.ui.alarm.SosAlarmActivity.EXTRA_CALLER_NAME,
                    com.example.alarm.SosAlarmManager.activeCallerName ?: com.example.alarm.SosAlarmManager.activeCallerNumber
                )
                putExtra(
                    com.example.ui.alarm.SosAlarmActivity.EXTRA_CALLER_NUMBER,
                    com.example.alarm.SosAlarmManager.activeCallerNumber
                )
                putExtra(
                    com.example.ui.alarm.SosAlarmActivity.EXTRA_MISSED_COUNT,
                    com.example.alarm.SosAlarmManager.activeMissedCount
                )
            }
            startActivity(alarmIntent)
        }
    }
}
