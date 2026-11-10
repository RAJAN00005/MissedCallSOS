package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.SosRepository
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.GlassStepper
import com.example.ui.components.GlassSwitch
import com.example.ui.components.GlassTextField
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import com.example.util.DeveloperInfo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: SosRepository,
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = repository.preferences

    val threshold by preferences.thresholdFlow.collectAsState()
    val windowMinutes by preferences.windowMinutesFlow.collectAsState()
    val cooldownMinutes by preferences.cooldownMinutesFlow.collectAsState()
    val visualQuality by preferences.visualQualityFlow.collectAsState()
    val reduceTransparency by preferences.reduceTransparencyFlow.collectAsState()
    val reduceMotion by preferences.reduceMotionFlow.collectAsState()
    val language by preferences.languageFlow.collectAsState()

    var flashlight by remember { mutableStateOf(preferences.isFlashlightEnabled()) }
    var vibration by remember { mutableStateOf(preferences.isVibrationEnabled()) }
    var pinRequired by remember { mutableStateOf(preferences.isPinRequired()) }
    var pinCode by remember { mutableStateOf(preferences.getAlarmPin()) }
    var simSlot by remember { mutableStateOf(preferences.getSimSlotForSms()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemoveAllContactsConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.nav_settings),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Trigger Rules Section
        SettingsSectionHeader(title = stringResource(R.string.settings_trigger_rules), icon = Icons.Default.Timer)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_threshold_label),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Calls needed to sound alarm",
                            color = GlassTheme.tokens.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    GlassStepper(
                        value = threshold,
                        onValueChange = { preferences.setGlobalThreshold(it) },
                        min = 2,
                        max = 20
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.settings_window_label),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 30, 60, 120).forEach { mins ->
                        GlassChip(
                            text = "${mins}m",
                            selected = windowMinutes == mins,
                            onClick = { preferences.setGlobalWindowMinutes(mins) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Preview Sentence
                Text(
                    text = "Summary: Alarm will ring after $threshold missed calls within $windowMinutes minutes.",
                    color = DesignTokens.StatusInfoBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Alarm Sound & Alert Section
        SettingsSectionHeader(title = stringResource(R.string.settings_alarm_sound), icon = Icons.Default.VolumeUp)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Flashlight toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = DesignTokens.StatusAttentionAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.settings_flash_toggle),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    GlassSwitch(
                        checked = flashlight,
                        onCheckedChange = {
                            flashlight = it
                            preferences.setFlashlightEnabled(it)
                        }
                    )
                }

                // Vibration toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null,
                            tint = DesignTokens.StatusInfoBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.settings_vibrate_toggle),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    GlassSwitch(
                        checked = vibration,
                        onCheckedChange = {
                            vibration = it
                            preferences.setVibrationEnabled(it)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Dual SIM Configuration
        SettingsSectionHeader(title = stringResource(R.string.settings_dual_sim_sms), icon = Icons.Default.SimCard)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_sim_slot),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        text = stringResource(R.string.settings_sim_auto),
                        selected = simSlot == -1,
                        onClick = {
                            simSlot = -1
                            preferences.setSimSlotForSms(-1)
                        }
                    )
                    GlassChip(
                        text = stringResource(R.string.settings_sim_1),
                        selected = simSlot == 0,
                        onClick = {
                            simSlot = 0
                            preferences.setSimSlotForSms(0)
                        }
                    )
                    GlassChip(
                        text = stringResource(R.string.settings_sim_2),
                        selected = simSlot == 1,
                        onClick = {
                            simSlot = 1
                            preferences.setSimSlotForSms(1)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Security & PIN
        SettingsSectionHeader(title = stringResource(R.string.settings_security), icon = Icons.Default.Lock)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_pin_protect),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    GlassSwitch(
                        checked = pinRequired,
                        onCheckedChange = {
                            pinRequired = it
                            preferences.setPinRequired(it)
                        }
                    )
                }

                if (pinRequired) {
                    GlassTextField(
                        value = pinCode,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinCode = it
                                preferences.setAlarmPin(it)
                            }
                        },
                        placeholder = "4-digit PIN (default: 1234)"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Appearance & Accessibility
        SettingsSectionHeader(title = stringResource(R.string.settings_appearance), icon = Icons.Default.Palette)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Visual Quality
                Column {
                    Text(
                        text = stringResource(R.string.settings_quality),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("auto", "high", "balanced", "minimal").forEach { q ->
                            GlassChip(
                                text = q.replaceFirstChar { it.uppercase() },
                                selected = visualQuality == q,
                                onClick = { preferences.setVisualQuality(q) }
                            )
                        }
                    }
                }

                // Reduce Transparency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_reduce_transparency),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    GlassSwitch(
                        checked = reduceTransparency,
                        onCheckedChange = { preferences.setReduceTransparency(it) }
                    )
                }

                // Reduce Motion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_reduce_motion),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    GlassSwitch(
                        checked = reduceMotion,
                        onCheckedChange = { preferences.setReduceMotion(it) }
                    )
                }

                // Ultra Battery Saver Mode
                val batterySaver by preferences.batterySaverFlow.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = DesignTokens.StatusActiveGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ultra Battery Saver",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Freezes GPU canvas animations & relies on 100% passive event triggers",
                            color = GlassTheme.tokens.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    GlassSwitch(
                        checked = batterySaver,
                        onCheckedChange = { preferences.setBatterySaverMode(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6. Data & Privacy
        SettingsSectionHeader(title = stringResource(R.string.settings_privacy), icon = Icons.Default.Delete)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        text = "Remove all registered emergency contacts and their custom rules.",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassButton(
                        onClick = { showRemoveAllContactsConfirm = true },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_all_contacts), fontWeight = FontWeight.SemiBold)
                    }
                }

                Column {
                    Text(
                        text = "Wipe all call events, alerts history, SMS outbox, and reset settings.",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassButton(
                        onClick = { showDeleteConfirm = true },
                        variant = GlassButtonVariant.DESTRUCTIVE,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_delete_all), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 7. About App & Developer
        SettingsSectionHeader(title = "About", icon = Icons.Default.Info)
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable { onNavigateToAbout() },
            shape = RoundedCornerShape(22.dp),
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App logo",
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "About MissedCall SOS",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version ${DeveloperInfo.versionName} • Developer, licenses & support",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open Full About Screen",
                    tint = GlassTheme.tokens.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showRemoveAllContactsConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAllContactsConfirm = false },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text(stringResource(R.string.remove_all_contacts)) },
            text = { Text(stringResource(R.string.remove_all_contacts_confirm)) },
            confirmButton = {
                GlassButton(
                    onClick = {
                        scope.launch {
                            repository.removeAllContacts(deleteHistory = false)
                            showRemoveAllContactsConfirm = false
                        }
                    },
                    variant = GlassButtonVariant.DESTRUCTIVE
                ) {
                    Text("Remove All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassButton(onClick = { showRemoveAllContactsConfirm = false }, variant = GlassButtonVariant.SECONDARY) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm)) },
            confirmButton = {
                GlassButton(
                    onClick = {
                        scope.launch {
                            repository.deleteAllData()
                            showDeleteConfirm = false
                        }
                    },
                    variant = GlassButtonVariant.DESTRUCTIVE
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                GlassButton(
                    onClick = { showDeleteConfirm = false },
                    variant = GlassButtonVariant.SECONDARY
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DesignTokens.StatusInfoBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
