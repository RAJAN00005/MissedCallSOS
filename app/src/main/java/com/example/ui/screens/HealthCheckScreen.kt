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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
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

    fun refreshHealth() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        statusMap["service"] = repository.preferences.isProtectionActive()
        statusMap["dnd"] = nm.isNotificationPolicyAccessGranted
        statusMap["battery"] = pm.isIgnoringBatteryOptimizations(context.packageName)
        statusMap["phone"] = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        statusMap["fullscreen"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else {
            true
        }
        statusMap["overlay"] = Settings.canDrawOverlays(context)
        statusMap["oem"] = true // manual instructions item
    }

    LaunchedEffect(Unit) {
        refreshHealth()
    }

    val healthList = remember {
        listOf(
            HealthItem("service", "Protection Service Active", "Monitors calls continuously in foreground", Icons.Default.Shield),
            HealthItem("dnd", "DND Policy Bypass", "Required to play loud alarm over silent/DND mode", Icons.Default.DoNotDisturbOn),
            HealthItem("battery", "Battery Optimization Ignored", "Prevents Android from stopping detection", Icons.Default.BatteryAlert),
            HealthItem("overlay", "Display Over Apps & Lockscreen", "Required to pop up alarm over lockscreen & other apps", Icons.Default.Layers),
            HealthItem("fullscreen", "Full-Screen Lockscreen Intent", "Opens alarm screen over lockscreen", Icons.Default.Fullscreen),
            HealthItem("phone", "Phone State Monitoring", "Detects real-time incoming and missed calls", Icons.Default.Phone),
            HealthItem("oem", "OEM Background Auto-Start Guide", "Settings for Xiaomi, Samsung, Oppo lockscreen popups", Icons.Default.Build)
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
            shape = RoundedCornerShape(22.dp),
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Ring / Circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (healthyCount == totalCount) "Protection Rock Solid!" else "Action Required",
                        color = if (healthyCount == totalCount) DesignTokens.StatusActiveGreen else DesignTokens.StatusAttentionAmber,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (healthyCount == totalCount) stringResource(R.string.health_all_good)
                        else "Some settings may prevent emergency alerts from sounding reliably.",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Health Items Checklist
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(healthList) { item ->
                val isHealthy = statusMap[item.id] == true

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status Icon
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = item.desc,
                                color = GlassTheme.tokens.textSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Fix button
                        if (item.id == "oem") {
                            GlassButton(
                                onClick = { showOemDialog = true },
                                variant = GlassButtonVariant.SECONDARY,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Guide", fontSize = 11.sp)
                            }
                        } else if (!isHealthy) {
                            GlassButton(
                                onClick = {
                                    when (item.id) {
                                        "service" -> {
                                            repository.preferences.setProtectionPausedUntil(0L)
                                            CallDetectionService.start(context)
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
                                        "overlay" -> {
                                            val manufacturer = Build.MANUFACTURER.uppercase()
                                            var handled = false
                                            if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO")) {
                                                try {
                                                    val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                                                        setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                                        putExtra("extra_pkgname", context.packageName)
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    context.startActivity(miuiIntent)
                                                    handled = true
                                                } catch (_: Exception) {}
                                            }
                                            if (!handled) {
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
                                        }
                                        "fullscreen" -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    // Fallback
                                                }
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
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(stringResource(R.string.fix_action), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
