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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Sms
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.service.CallDetectionService
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.ProtectionVisualStatus

data class PermissionStep(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val isSystemSetting: Boolean = false
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionStatus = remember { mutableStateMapOf<String, Boolean>() }

    val steps = remember {
        buildList {
            add(PermissionStep("phone", R.string.permission_phone_title, R.string.permission_phone_desc, Icons.Default.Phone))
            add(PermissionStep("call_log", R.string.permission_call_log_title, R.string.permission_call_log_desc, Icons.Default.PhoneCallback))
            add(PermissionStep("contacts", R.string.permission_contacts_title, R.string.permission_contacts_desc, Icons.Default.Contacts))
            add(PermissionStep("overlay", R.string.permission_overlay_title, R.string.permission_overlay_desc, Icons.Default.Layers, isSystemSetting = true))
            add(PermissionStep("dnd", R.string.permission_dnd_title, R.string.permission_dnd_desc, Icons.Default.DoNotDisturbOn, isSystemSetting = true))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(PermissionStep("fullscreen", R.string.permission_fullscreen_title, R.string.permission_fullscreen_desc, Icons.Default.Fullscreen, isSystemSetting = true))
            }
            add(PermissionStep("battery", R.string.permission_battery_title, R.string.permission_battery_desc, Icons.Default.BatteryAlert, isSystemSetting = true))
        }
    }

    fun checkAll() {
        permissionStatus["phone"] = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        permissionStatus["call_log"] = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        permissionStatus["contacts"] = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        permissionStatus["dnd"] = nm.isNotificationPolicyAccessGranted
        permissionStatus["overlay"] = Settings.canDrawOverlays(context)
        permissionStatus["fullscreen"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else {
            true
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        permissionStatus["battery"] = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        checkAll()
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkAll()
    }

    GlassBackground(status = ProtectionVisualStatus.ACTIVE) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App logo",
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (com.example.util.DeveloperInfo.hasDeveloperName) {
                        Text(
                            text = "by ${com.example.util.DeveloperInfo.developerName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DesignTokens.StatusInfoBlue
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                fontSize = 14.sp,
                color = GlassTheme.tokens.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Permissions list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(steps) { step ->
                    val isGranted = permissionStatus[step.id] == true

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isGranted) DesignTokens.StatusActiveGreen.copy(alpha = 0.2f)
                                        else DesignTokens.StatusInfoBlue.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isGranted) DesignTokens.StatusActiveGreen else Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = if (isGranted) DesignTokens.StatusActiveGreen else DesignTokens.StatusInfoBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(step.titleRes),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(step.descRes),
                                    color = GlassTheme.tokens.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Action button or granted chip
                            if (isGranted) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(DesignTokens.StatusActiveGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Granted",
                                            tint = DesignTokens.StatusActiveGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.status_granted),
                                            color = DesignTokens.StatusActiveGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                GlassButton(
                                    onClick = {
                                        when (step.id) {
                                            "phone" -> multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                                            "call_log" -> multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
                                            "contacts" -> multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                            "overlay" -> {
                                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    })
                                                }
                                            }
                                            "dnd" -> {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                                context.startActivity(intent)
                                            }
                                            "fullscreen" -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.parse("package:${context.packageName}")
                                                        })
                                                    }
                                                }
                                            }
                                            "battery" -> {
                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    // Fallback
                                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                                }
                                            }
                                        }
                                    },
                                    variant = GlassButtonVariant.SECONDARY,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.fix_action),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue button
            GlassButton(
                onClick = {
                    CallDetectionService.start(context)
                    onComplete()
                },
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.get_started),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
