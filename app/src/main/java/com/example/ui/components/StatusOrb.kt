package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.ProtectionVisualStatus

@Composable
fun StatusOrb(
    status: ProtectionVisualStatus,
    modifier: Modifier = Modifier
) {
    val reduceMotion = GlassTheme.reduceMotion

    val (targetColor, targetPulseDuration) = when (status) {
        ProtectionVisualStatus.ACTIVE -> Pair(DesignTokens.StatusActiveGreen, 2600)
        ProtectionVisualStatus.ATTENTION -> Pair(DesignTokens.StatusAttentionAmber, 1800)
        ProtectionVisualStatus.CRITICAL -> Pair(DesignTokens.StatusCriticalRed, 900)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(600),
        label = "orb_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(targetPulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    val scale = if (reduceMotion) 1.0f else pulseScale

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glowing canvas sphere
        Canvas(modifier = Modifier.size(160.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.72f
            val pulseRadius = baseRadius * scale

            // Outer soft glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedColor.copy(alpha = 0.35f),
                        animatedColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = pulseRadius * 1.35f
                ),
                radius = pulseRadius * 1.35f,
                center = center
            )

            // Inner translucent glass orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        animatedColor.copy(alpha = 0.50f),
                        animatedColor.copy(alpha = 0.15f)
                    ),
                    center = Offset(center.x - baseRadius * 0.25f, center.y - baseRadius * 0.3f),
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // Specular top highlight crescent
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    start = Offset(center.x, center.y - baseRadius),
                    end = Offset(center.x, center.y - baseRadius * 0.2f)
                ),
                radius = baseRadius * 0.65f,
                center = Offset(center.x, center.y - baseRadius * 0.45f)
            )

            // Specular border ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f),
                        animatedColor.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.75f)
                    ),
                    center = center
                ),
                radius = baseRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )
        }

        // Center emblem
        val icon = when (status) {
            ProtectionVisualStatus.ACTIVE -> Icons.Default.Shield
            ProtectionVisualStatus.ATTENTION -> Icons.Default.Warning
            ProtectionVisualStatus.CRITICAL -> Icons.Default.NotificationsActive
        }

        Icon(
            imageVector = icon,
            contentDescription = "Status Orb Emblem",
            tint = Color.White,
            modifier = Modifier.size(46.dp)
        )
    }
}
