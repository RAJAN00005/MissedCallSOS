package com.example.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.entity.TriggerHistoryEntity
import com.example.data.repository.SosRepository
import com.example.service.CallDetectionService
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusOrb
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.ProtectionVisualStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    repository: SosRepository,
    onNavigateToHealth: () -> Unit,
    onNavigateToTest: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val pausedUntil by repository.preferences.pausedUntilFlow.collectAsState()
    val isPaused = pausedUntil > System.currentTimeMillis()

    val callsToday by repository.getTodayCallsCountFlow().collectAsState(initial = 0)
    val triggersFired by repository.totalTriggersCountFlow.collectAsState(initial = 0)
    val totalContacts by repository.allRulesFlow.collectAsState(initial = emptyList())
    val latestTrigger by repository.latestTriggerFlow.collectAsState(initial = null)

    // Check permissions for health warning banner
    val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    val hasDnd = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
    val hasOverlay = Settings.canDrawOverlays(context)
    val isMissingPermissions = !hasPhone || !hasCallLog || !hasDnd || !hasOverlay

    val visualStatus = when {
        isMissingPermissions -> ProtectionVisualStatus.ATTENTION
        isPaused -> ProtectionVisualStatus.ATTENTION
        else -> ProtectionVisualStatus.ACTIVE
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Emergency Repeated Call Guard",
                    color = GlassTheme.tokens.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Test Mode Button
                Box(
                    modifier = Modifier
                        .widthIn(min = 76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .clickable { onNavigateToTest() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Test Mode",
                            tint = DesignTokens.StatusInfoBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Active Emergency SOS Alarm Banner (if currently sounding)
        val isAlarmActive = com.example.alarm.SosAlarmManager.isAlarmActive
        if (isAlarmActive) {
            Spacer(modifier = Modifier.height(10.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val alarmIntent = Intent(context, com.example.ui.alarm.SosAlarmActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
                        context.startActivity(alarmIntent)
                    },
                shape = RoundedCornerShape(18.dp),
                borderBrush = Brush.linearGradient(listOf(DesignTokens.StatusCriticalRed, Color.Transparent)),
                fillBrush = Brush.verticalGradient(
                    listOf(
                        DesignTokens.StatusCriticalRed.copy(alpha = 0.5f),
                        Color(0xFF330B12).copy(alpha = 0.9f)
                    )
                ),
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = DesignTokens.StatusCriticalRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🚨 SOS ALARM ACTIVE!",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to open dismissal screen",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                    GlassButton(
                        onClick = {
                            com.example.service.SosAlarmService.stop(context)
                        },
                        variant = GlassButtonVariant.DESTRUCTIVE,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("STOP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Missing permissions warning banner
        if (isMissingPermissions) {
            Spacer(modifier = Modifier.height(10.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                borderBrush = Brush.linearGradient(listOf(DesignTokens.StatusAttentionAmber, Color.Transparent)),
                fillBrush = Brush.verticalGradient(
                    listOf(
                        DesignTokens.StatusAttentionAmber.copy(alpha = 0.25f),
                        DesignTokens.NavyTintAmber.copy(alpha = 0.6f)
                    )
                ),
                contentPadding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DesignTokens.StatusAttentionAmber,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.status_attention),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Grant Overlay & DND permissions for lockscreen alarm popup.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                    GlassButton(
                        onClick = onNavigateToHealth,
                        variant = GlassButtonVariant.SECONDARY,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.fix_action),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Center StatusOrb
        StatusOrb(status = visualStatus)

        Spacer(modifier = Modifier.height(16.dp))

        // Status text
        Text(
            text = if (isPaused) stringResource(R.string.status_paused) else stringResource(R.string.status_active),
            color = if (isPaused) DesignTokens.StatusAttentionAmber else DesignTokens.StatusActiveGreen,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        val statusDesc = if (isPaused) {
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            stringResource(R.string.status_desc_paused, format.format(Date(pausedUntil)))
        } else {
            stringResource(R.string.status_desc_active)
        }

        Text(
            text = statusDesc,
            color = GlassTheme.tokens.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Pause / Resume Control
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isPaused) {
                // Resume button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(DesignTokens.StatusActiveGreen.copy(alpha = 0.25f))
                        .clickable {
                            repository.preferences.setProtectionPausedUntil(0L)
                            CallDetectionService.start(context)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DesignTokens.StatusActiveGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.resume_protection),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // 1 Hour Pause
                PauseOptionPill("1h") {
                    val oneHour = System.currentTimeMillis() + 3600_000L
                    repository.preferences.setProtectionPausedUntil(oneHour)
                }
                // 8 Hours Pause
                PauseOptionPill("8h") {
                    val eightHours = System.currentTimeMillis() + 8 * 3600_000L
                    repository.preferences.setProtectionPausedUntil(eightHours)
                }
                // Until Resume
                PauseOptionPill("Until Resume") {
                    val future = System.currentTimeMillis() + 7 * 24 * 3600_000L
                    repository.preferences.setProtectionPausedUntil(future)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3 Stat Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                count = callsToday.toString(),
                label = stringResource(R.string.stat_missed_today),
                icon = Icons.Default.CallReceived,
                color = DesignTokens.StatusInfoBlue,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                count = triggersFired.toString(),
                label = stringResource(R.string.stat_triggers_fired),
                icon = Icons.Default.NotificationsActive,
                color = DesignTokens.StatusCriticalRed,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                count = totalContacts.size.toString(),
                label = stringResource(R.string.stat_protected_contacts),
                icon = Icons.Default.People,
                color = DesignTokens.StatusActiveGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Last Trigger Card
        Text(
            text = stringResource(R.string.last_trigger_title),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        if (latestTrigger != null) {
            LastTriggerCard(trigger = latestTrigger!!)
        } else {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = 16.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DesignTokens.StatusActiveGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.no_recent_triggers),
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // About Developer & App Card directly on Home Screen
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(DesignTokens.StatusInfoBlue.copy(alpha = 0.2f))
                                .border(1.dp, DesignTokens.StatusInfoBlue.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = DesignTokens.StatusInfoBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Developed By RAAJAN",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Email: missedcallsos@gmail.com",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onNavigateToAbout() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "About",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PauseOptionPill(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = GlassTheme.tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatTile(
    count: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 12.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = GlassTheme.tokens.textSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LastTriggerCard(trigger: TriggerHistoryEntity) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(trigger.triggeredAt))

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DesignTokens.StatusCriticalRed.copy(alpha = 0.2f))
                    .border(1.dp, DesignTokens.StatusCriticalRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = DesignTokens.StatusCriticalRed,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trigger.callerName ?: trigger.callerNumber,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${trigger.missedCount} missed calls • $dateStr",
                    color = GlassTheme.tokens.textSecondary,
                    fontSize = 12.sp
                )
                if (trigger.deliveryStatus == "ALARM_FIRED") {
                    Text(
                        text = "SOS Loud Alarm sounded",
                        color = DesignTokens.StatusActiveGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
