package com.example.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.SosRepository
import com.example.service.CallDetectionService
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme

data class HealthItem(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector
)

@Composable
fun HealthCheckScreen(
    repository: SosRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusMap = remember { mutableStateMapOf<String, Boolean>() }
    var showOemDialog by remember { mutableStateOf(false) }

    val corePermissionsList = remember {
        buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    fun isCallScreeningActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                rm == null ||
                    !rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) ||
                    rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            } catch (_: Exception) {
                true
            }
        }
        return true
    }

    fun refreshHealth() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        statusMap["core_telephony"] = hasPhone && hasCallLog && hasContacts && hasNotification
        statusMap["service"] = repository.preferences.isProtectionActive() && isCallScreeningActive()
        statusMap["dnd"] = nm.isNotificationPolicyAccessGranted
        statusMap["battery"] = pm.isIgnoringBatteryOptimizations(context.packageName)
        statusMap["display_screen"] = Settings.canDrawOverlays(context)
        statusMap["oem"] = true
    }

    LaunchedEffect(Unit) {
        refreshHealth()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(context)
        refreshHealth()
    }

    val callScreeningRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(context)
        refreshHealth()
    }

    val healthList = remember {
        listOf(
            HealthItem("core_telephony", "Call & Contacts Shield", "Detects repeated calls & trusted emergency contacts.", Icons.Default.Phone),
            HealthItem("display_screen", "Lockscreen Alarm Display", "Shows emergency alarm dismiss screen over lockscreen.", Icons.Default.Layers),
            HealthItem("dnd", "Silent Mode Alarm Bypass", "Allows siren to ring at full volume in Silent or DND.", Icons.Default.DoNotDisturbOn),
            HealthItem("battery", "24/7 Background Guard", "Keeps call monitoring alive when screen is off.", Icons.Default.BatteryAlert),
            HealthItem("service", "Background Call Trigger", "Wakes SOS alarm even when swiped from Recents (0% battery).", Icons.Default.Shield),
            HealthItem("oem", "OEM Auto-Start Guide", "Settings for Xiaomi, Samsung, Oppo lockscreen popups.", Icons.Default.Build)
        )
    }

    val healthyCount = healthList.count { statusMap[it.id] == true }
    val totalCount = healthList.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.health_check_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Health Score Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Ring
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (healthyCount == totalCount) DesignTokens.StatusActiveGreen.copy(alpha = 0.2f)
                            else DesignTokens.StatusAttentionAmber.copy(alpha = 0.2f)
                        )
                        .border(
                            2.dp,
                            if (healthyCount == totalCount) DesignTokens.StatusActiveGreen else DesignTokens.StatusAttentionAmber,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$healthyCount/$totalCount",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (healthyCount == totalCount) "Protection Rock Solid!" else "Action Required",
                        color = if (healthyCount == totalCount) DesignTokens.StatusActiveGreen else DesignTokens.StatusAttentionAmber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (healthyCount == totalCount) stringResource(R.string.health_all_good)
                        else "Tap Allow to grant missing permissions.",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Health Items Checklist - Uniform Sleek Cards
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(healthList) { item ->
                val isHealthy = statusMap[item.id] == true

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status Icon Box
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHealthy) DesignTokens.StatusActiveGreen.copy(alpha = 0.2f)
                                    else DesignTokens.StatusCriticalRed.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isHealthy) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isHealthy) DesignTokens.StatusActiveGreen else DesignTokens.StatusCriticalRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Details
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.desc,
                                color = GlassTheme.tokens.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Uniform Action / Status button - Exactly 74.dp width and 36.dp height
                        if (item.id == "oem") {
                            GlassButton(
                                onClick = { showOemDialog = true },
                                variant = GlassButtonVariant.SECONDARY,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(width = 74.dp, height = 36.dp)
                            ) {
                                Text("Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isHealthy) {
                            Box(
                                modifier = Modifier
                                    .size(width = 74.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DesignTokens.StatusActiveGreen.copy(alpha = 0.18f))
                                    .border(1.dp, DesignTokens.StatusActiveGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = DesignTokens.StatusActiveGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Active",
                                        color = DesignTokens.StatusActiveGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            GlassButton(
                                onClick = {
                                    when (item.id) {
                                        "core_telephony" -> {
                                            multiplePermissionLauncher.launch(corePermissionsList)
                                        }
                                        "service" -> {
                                            repository.preferences.setProtectionPausedUntil(0L)
                                            CallDetectionService.start(context)
                                            com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(context)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                try {
                                                    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                                                    if (rm != null &&
                                                        rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) &&
                                                        !rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                                                    ) {
                                                        callScreeningRoleLauncher.launch(
                                                            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                                                        )
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                            refreshHealth()
                                        }
                                        "dnd" -> {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                        "battery" -> {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                            }
                                        }
                                        "display_screen" -> {
                                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                })
                                            }
                                        }
                                        else -> {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                },
                                variant = GlassButtonVariant.SECONDARY,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(width = 74.dp, height = 36.dp)
                            ) {
                                Text(
                                    text = "Allow",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // OEM Background Instructions Dialog
    if (showOemDialog) {
        val manufacturer = Build.MANUFACTURER.uppercase()
        val oemText = when {
            manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO") ->
                "Xiaomi/MIUI Instructions (CRITICAL FOR LOCKSCREEN ALARM):\n1. Open Settings -> Apps -> Manage apps -> MissedCall SOS.\n2. Turn ON 'Autostart'.\n3. Tap 'Other permissions' and turn ON 'Show on Lock screen' and 'Display pop-up windows while running in the background'.\n4. Tap 'Battery saver' and select 'No restrictions'."
            manufacturer.contains("SAMSUNG") ->
                "Samsung Instructions:\n1. Open Settings -> Apps -> MissedCall SOS -> Battery.\n2. Select 'Unrestricted'.\n3. In Device Care -> Battery -> Background usage limits, ensure MissedCall SOS is in 'Never sleeping apps'."
            manufacturer.contains("OPPO") || manufacturer.contains("REALME") ->
                "Oppo/Realme Instructions:\n1. Open Settings -> App Management -> MissedCall SOS.\n2. Allow 'Auto startup'.\n3. Enable 'Allow background activity'."
            manufacturer.contains("VIVO") || manufacturer.contains("IQOO") ->
                "Vivo/iQOO Instructions:\n1. Open Settings -> Battery -> High background power consumption.\n2. Toggle ON MissedCall SOS."
            manufacturer.contains("ONEPLUS") ->
                "OnePlus Instructions:\n1. Open Settings -> Battery -> Battery optimization.\n2. Find MissedCall SOS and choose 'Don't optimize'."
            else ->
                "General OEM Instructions:\n1. Ensure MissedCall SOS has 'Autostart' enabled in device settings.\n2. Turn off battery optimization for MissedCall SOS.\n3. Turn ON 'Show on Lock screen' in app permissions."
        }

        AlertDialog(
            onDismissRequest = { showOemDialog = false },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text("Device: ${Build.MANUFACTURER} ($manufacturer)") },
            text = { Text(oemText, lineHeight = 20.sp, fontSize = 13.sp) },
            confirmButton = {
                GlassButton(
                    onClick = {
                        showOemDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    variant = GlassButtonVariant.PRIMARY
                ) {
                    Text("Open App Settings")
                }
            },
            dismissButton = {
                GlassButton(onClick = { showOemDialog = false }, variant = GlassButtonVariant.SECONDARY) {
                    Text("Close")
                }
            }
        )
    }
}
