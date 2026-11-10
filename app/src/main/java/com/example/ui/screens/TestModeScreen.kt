package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.entity.ContactRuleEntity
import com.example.data.repository.SosRepository
import com.example.telephony.CallStateTracker
import com.example.telephony.PhoneNumberHelper
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.GlassStepper
import com.example.ui.components.GlassTextField
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TestModeScreen(
    repository: SosRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRules by repository.allRulesFlow.collectAsState(initial = emptyList())

    var testNumber by remember { mutableStateOf("") }
    var testName by remember { mutableStateOf("") }
    var callsToSimulate by remember { mutableStateOf(3) }
    var isRunning by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf("") }
    var stepLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSmsWarningDialog by remember { mutableStateOf(false) }
    var purgeStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.test_mode_title),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Runs REAL pipeline with isTest = true isolation",
                    color = GlassTheme.tokens.textSecondary,
                    fontSize = 12.sp
                )
            }

            GlassButton(
                onClick = {
                    scope.launch {
                        repository.purgeAllTestData()
                        purgeStatus = "Purged all test events"
                        delay(2000)
                        purgeStatus = null
                    }
                },
                variant = GlassButtonVariant.SECONDARY,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear Test Data", fontSize = 11.sp)
            }
        }

        if (purgeStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = purgeStatus!!, color = DesignTokens.StatusActiveGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Setup Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Simulation Settings",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Select from added contacts if available
                if (allRules.isNotEmpty()) {
                    Text("Select Contact to Test:", color = GlassTheme.tokens.textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allRules.take(3).forEach { rule ->
                            GlassChip(
                                text = rule.displayName,
                                selected = testNumber == rule.normalizedNumber,
                                onClick = {
                                    testName = rule.displayName
                                    testNumber = rule.normalizedNumber
                                }
                            )
                        }
                    }
                }

                GlassTextField(
                    value = testName,
                    onValueChange = { testName = it },
                    placeholder = "Caller Name (e.g. My Second Phone)"
                )

                GlassTextField(
                    value = testNumber,
                    onValueChange = { testNumber = it },
                    placeholder = "Caller Phone Number"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.test_calls_to_simulate),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    GlassStepper(
                        value = callsToSimulate,
                        onValueChange = { callsToSimulate = it },
                        min = 1,
                        max = 10
                    )
                }

                val canRun = testName.isNotBlank() && testNumber.isNotBlank()

                GlassButton(
                    onClick = {
                        showSmsWarningDialog = true
                    },
                    enabled = canRun && !isRunning,
                    loading = isRunning,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.test_run_simulation), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Simulation Logs Card
        if (stepLogs.isNotEmpty() || isRunning) {
            Text(
                text = "Simulation Pipeline Output (TEST Mode)",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = 16.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stepLogs.forEach { log ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(DesignTokens.StatusActiveGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = DesignTokens.StatusActiveGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = log,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DesignTokens.StatusInfoBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = currentStep,
                                color = DesignTokens.StatusInfoBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showSmsWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSmsWarningDialog = false },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = DesignTokens.StatusAttentionAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Test Simulation")
                }
            },
            text = {
                Text("This test will execute the REAL counting pipeline with isTest = true (isolated from Home stats).\n\nAn emergency alert SMS will be queued for $testNumber.\n\nProceed?")
            },
            confirmButton = {
                GlassButton(
                    onClick = {
                        showSmsWarningDialog = false
                        isRunning = true
                        stepLogs = emptyList()
                        scope.launch {
                            val norm = PhoneNumberHelper.normalizeToE164(testNumber)

                            // Ensure contact rule exists so trigger can fire
                            var rule = repository.getRuleForNumber(norm)
                            if (rule == null) {
                                rule = ContactRuleEntity(
                                    displayName = testName,
                                    rawNumber = testNumber,
                                    normalizedNumber = norm,
                                    isAlarmEnabled = true,
                                    isLocationEnabled = false,
                                    customThreshold = callsToSimulate
                                )
                                repository.saveRule(rule)
                            }

                            for (i in 1..callsToSimulate) {
                                currentStep = "Simulating incoming call $i of $callsToSimulate (isTest = true)..."
                                stepLogs = stepLogs + "[TEST] Call $i from $testName ($norm)"
                                CallStateTracker.processMissedCall(
                                    context = context,
                                    repository = repository,
                                    rawNumber = testNumber,
                                    timestamp = System.currentTimeMillis() - (callsToSimulate - i) * 1000L,
                                    simSlot = 0,
                                    isTest = true
                                )
                                delay(300)
                            }

                            currentStep = "Threshold reached! SOS Alarm & Emergency SMS triggered (isTest = true)!"
                            stepLogs = stepLogs + "[TEST] Threshold reached ($callsToSimulate calls). Alarm and SMS queued."
                            isRunning = false
                        }
                    },
                    variant = GlassButtonVariant.PRIMARY
                ) {
                    Text("Run Test", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassButton(onClick = { showSmsWarningDialog = false }, variant = GlassButtonVariant.SECONDARY) {
                    Text("Cancel")
                }
            }
        )
    }
}
