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
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val title: String,
    val desc: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionStatus = remember { mutableStateMapOf<String, Boolean>() }

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

    val steps = remember {
        listOf(
            PermissionStep(
                id = "core_telephony",
                title = "Call & Contacts Shield",
                desc = "Detects repeated calls & trusted emergency contacts.",
                icon = Icons.Default.Phone
            ),
            PermissionStep(
                id = "display_overlay",
                title = "Lockscreen Alarm Display",
                desc = "Shows red alarm screen with dismiss button over lockscreen.",
                icon = Icons.Default.Layers
            ),
            PermissionStep(
                id = "dnd",
                title = "Silent Mode Alarm Bypass",
                desc = "Allows siren to ring at full volume in Silent or DND.",
                icon = Icons.Default.DoNotDisturbOn
            ),
            PermissionStep(
                id = "battery",
                title = "24/7 Background Guard",
                desc = "Keeps call monitoring alive when screen is off.",
                icon = Icons.Default.BatteryAlert
            )
        )
    }

    fun checkAll() {
        val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        permissionStatus["core_telephony"] = hasPhone && hasCallLog && hasContacts && hasNotification

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        permissionStatus["dnd"] = nm.isNotificationPolicyAccessGranted
        permissionStatus["display_overlay"] = Settings.canDrawOverlays(context)

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

    val grantedCount = steps.count { permissionStatus[it.id] == true }
    val totalCount = steps.size

    GlassBackground(status = ProtectionVisualStatus.ACTIVE) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App logo",
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permissions are simplified into 4 sleek steps so you can get protected quickly.",
                fontSize = 13.sp,
                color = GlassTheme.tokens.textSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Uniform Sleek Permissions list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(steps) { step ->
                    val isGranted = permissionStatus[step.id] == true

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Uniform Icon Box
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isGranted) DesignTokens.StatusActiveGreen.copy(alpha = 0.2f)
                                        else DesignTokens.StatusInfoBlue.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isGranted) DesignTokens.StatusActiveGreen else Color.White.copy(alpha = 0.18f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = if (isGranted) DesignTokens.StatusActiveGreen else DesignTokens.StatusInfoBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details (sleek typography, no word-wrapping badges)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = step.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = step.desc,
                                    color = GlassTheme.tokens.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Uniform Action / Status Pill - Exactly 74.dp width and 36.dp height on all cards
                            if (isGranted) {
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
                                            contentDescription = "Allowed",
                                            tint = DesignTokens.StatusActiveGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Allowed",
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
                                            "core_telephony" -> {
                                                multiplePermissionLauncher.launch(corePermissionsList)
                                            }
                                            "display_overlay" -> {
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
                                                    } catch (_: Exception) {
                                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.parse("package:${context.packageName}")
                                                        })
                                                    }
                                                }
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue button
            GlassButton(
                onClick = {
                    CallDetectionService.start(context)
                    onComplete()
                },
                variant = GlassButtonVariant.PRIMARY,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (grantedCount == totalCount) "All Set - Start Protection" else stringResource(R.string.get_started),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
