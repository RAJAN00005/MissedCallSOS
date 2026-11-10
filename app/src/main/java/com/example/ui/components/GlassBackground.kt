package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTier
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.ProtectionVisualStatus

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    status: ProtectionVisualStatus = ProtectionVisualStatus.ACTIVE,
    content: @Composable () -> Unit
) {
    val tier = GlassTheme.tier
    val reduceMotion = GlassTheme.reduceMotion

    val targetStatusTint = when (status) {
        ProtectionVisualStatus.ACTIVE -> DesignTokens.NavyTintGreen
        ProtectionVisualStatus.ATTENTION -> DesignTokens.NavyTintAmber
        ProtectionVisualStatus.CRITICAL -> DesignTokens.NavyTintRed
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = targetStatusTint,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "status_color_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "blob_motion")

    // Slow drifting blob offsets (avoid animation overhead in battery saver / reduce motion)
    val blob1Offset by if (reduceMotion || tier == GlassTier.TIER_C) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -50f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(28000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blob1_x"
        )
    }

    val blob2Offset by if (reduceMotion || tier == GlassTier.TIER_C) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 40f,
            targetValue = -70f,
            animationSpec = infiniteRepeatable(
                animation = tween(34000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blob2_y"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DesignTokens.DeepNavy)
    ) {
        // Base subtle background mesh
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Status tint overlay
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedStatusColor.copy(alpha = 0.55f),
                        DesignTokens.DeepNavy
                    ),
                    center = Offset(width * 0.5f, height * 0.35f),
                    radius = width * 0.95f
                )
            )

            // If Tier A or B and animations are allowed, render soft background gradient blobs
            if (tier != GlassTier.TIER_C && !reduceMotion) {
                // Blob 1: Indigo top-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DesignTokens.BlobIndigo.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.25f + blob1Offset, height * 0.2f),
                        radius = width * 0.55f
                    ),
                    center = Offset(width * 0.25f + blob1Offset, height * 0.2f),
                    radius = width * 0.55f
                )

                // Blob 2: Teal center-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DesignTokens.BlobTeal.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.8f, height * 0.45f + blob2Offset),
                        radius = width * 0.50f
                    ),
                    center = Offset(width * 0.8f, height * 0.45f + blob2Offset),
                    radius = width * 0.50f
                )

                // Blob 3: Violet bottom-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DesignTokens.BlobViolet.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.2f, height * 0.75f - blob1Offset),
                        radius = width * 0.45f
                    ),
                    center = Offset(width * 0.2f, height * 0.75f - blob1Offset),
                    radius = width * 0.45f
                )

                // Blob 4: Magenta bottom-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DesignTokens.BlobMagenta.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.85f, height * 0.85f),
                        radius = width * 0.40f
                    ),
                    center = Offset(width * 0.85f, height * 0.85f),
                    radius = width * 0.40f
                )
            }
        }

        content()
    }
}
