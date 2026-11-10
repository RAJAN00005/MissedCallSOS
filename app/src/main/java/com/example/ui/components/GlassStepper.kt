package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme

@Composable
fun GlassStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int = 2,
    max: Int = 20,
    unitSuffix: String = "",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassTheme.tokens.surfaceFill)
            .border(1.dp, GlassTheme.tokens.borderBrush, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Decrement button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (value > min) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = value > min
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange((value - 1).coerceAtLeast(min))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = if (value > min) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textMuted
            )
        }

        // Value text
        Text(
            text = "$value$unitSuffix",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = GlassTheme.tokens.textPrimary,
            modifier = Modifier.defaultMinSize(minWidth = 36.dp)
        )

        // Increment button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (value < max) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = value < max
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange((value + 1).coerceAtMost(max))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = if (value < max) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textMuted
            )
        }
    }
}
