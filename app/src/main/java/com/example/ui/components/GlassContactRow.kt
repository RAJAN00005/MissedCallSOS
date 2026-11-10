package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactRuleEntity
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassContactRow(
    rule: ContactRuleEntity,
    onAlarmToggle: (Boolean) -> Unit,
    onEditThreshold: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val initial = rule.displayName.firstOrNull()?.uppercase() ?: "?"

    val avatarBrush = remember(rule.displayName) {
        val hash = rule.displayName.hashCode()
        when (kotlin.math.abs(hash) % 4) {
            0 -> Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)))
            1 -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
            2 -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF3B82F6)))
            else -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
        }
    }

    val borderColor = if (isSelected) DesignTokens.StatusInfoBlue else GlassTheme.tokens.borderBrush

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        onEditThreshold()
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        borderBrush = if (isSelected) Brush.linearGradient(listOf(DesignTokens.StatusInfoBlue, DesignTokens.StatusInfoBlue)) else GlassTheme.tokens.borderBrush,
        fillBrush = if (isSelected) {
            Brush.verticalGradient(listOf(DesignTokens.StatusInfoBlue.copy(alpha = 0.35f), Color(0xFF131B33)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF1B243B), Color(0xFF101728)))
        },
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator or Avatar
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) DesignTokens.StatusInfoBlue else Color.White.copy(alpha = 0.1f)
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(avatarBrush)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = rule.displayName,
                    color = GlassTheme.tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.normalizedNumber,
                    color = GlassTheme.tokens.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                if (rule.customThreshold != null) {
                    Text(
                        text = "Threshold: ${rule.customThreshold} calls",
                        color = DesignTokens.StatusInfoBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action toggles
            if (!isSelectionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Alarm Toggle Icon
                    val alarmBg by animateColorAsState(
                        targetValue = if (rule.isAlarmEnabled) DesignTokens.StatusActiveGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                        label = "alarm_toggle_bg"
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(alarmBg)
                            .border(
                                1.dp,
                                if (rule.isAlarmEnabled) DesignTokens.StatusActiveGreen else Color.White.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Switch
                            ) {
                                onAlarmToggle(!rule.isAlarmEnabled)
                            }
                            .semantics {
                                contentDescription = if (rule.isAlarmEnabled) "Alarm enabled for ${rule.displayName}" else "Alarm disabled for ${rule.displayName}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (rule.isAlarmEnabled) DesignTokens.StatusActiveGreen else GlassTheme.tokens.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edit settings / bottom sheet button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEditThreshold
                            )
                            .semantics {
                                contentDescription = "Edit rules for ${rule.displayName}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Direct Delete Button
                    if (onDeleteClick != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DesignTokens.StatusCriticalRed.copy(alpha = 0.18f))
                                .border(1.dp, DesignTokens.StatusCriticalRed.copy(alpha = 0.45f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDeleteClick
                                )
                                .semantics {
                                    contentDescription = "Remove contact ${rule.displayName}"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = DesignTokens.StatusCriticalRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
