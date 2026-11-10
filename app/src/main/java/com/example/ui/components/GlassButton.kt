package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme

enum class GlassButtonVariant {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !loading) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "btn_press_scale"
    )

    val (bgBrush, borderBrush, contentColor) = when (variant) {
        GlassButtonVariant.PRIMARY -> Triple(
            Brush.verticalGradient(
                listOf(
                    DesignTokens.StatusActiveGreen.copy(alpha = 0.85f),
                    Color(0xFF059669).copy(alpha = 0.90f)
                )
            ),
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
            ),
            Color.White
        )
        GlassButtonVariant.SECONDARY -> Triple(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.08f)
                )
            ),
            GlassTheme.tokens.borderBrush,
            GlassTheme.tokens.textPrimary
        )
        GlassButtonVariant.DESTRUCTIVE -> Triple(
            Brush.verticalGradient(
                listOf(
                    DesignTokens.StatusCriticalRed.copy(alpha = 0.85f),
                    Color(0xFFBE123C).copy(alpha = 0.90f)
                )
            ),
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
            ),
            Color.White
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (variant == GlassButtonVariant.PRIMARY) 10.dp else 4.dp,
                shape = shape,
                spotColor = if (variant == GlassButtonVariant.PRIMARY) DesignTokens.StatusActiveGreen.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f)
            )
            .clip(shape)
            .background(brush = if (enabled) bgBrush else Brush.verticalGradient(listOf(Color.Gray.copy(alpha = 0.2f), Color.Gray.copy(alpha = 0.1f))))
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    content()
                }
            }
        }
    }
}
