package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SlideToDismissTrack(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "SLIDE TO STOP ALARM"
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(12.dp, RoundedCornerShape(36.dp), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xFF2B0B13).copy(alpha = 0.90f))
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        DesignTokens.StatusCriticalRed.copy(alpha = 0.8f),
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                RoundedCornerShape(36.dp)
            )
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbSizePx = with(density) { 60.dp.toPx() }
        val maxDragPx = constraints.maxWidth.toFloat() - thumbSizePx - with(density) { 12.dp.toPx() }

        // Center track text
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        // Draggable thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(60.dp)
                .shadow(8.dp, CircleShape, spotColor = DesignTokens.StatusCriticalRed)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DesignTokens.StatusCriticalRed,
                            Color(0xFF9F1239)
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newValue = (dragOffset.value + delta).coerceIn(0f, maxDragPx)
                            dragOffset.snapTo(newValue)
                        }
                    },
                    onDragStarted = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragStopped = {
                        scope.launch {
                            if (dragOffset.value >= maxDragPx * 0.60f) {
                                // Successful dismiss
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragOffset.animateTo(maxDragPx, spring(dampingRatio = 0.8f))
                                onDismissed()
                            } else {
                                // Snap back
                                dragOffset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Slide thumb",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
