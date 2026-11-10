package com.example.ui.alarm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassTextField
import com.example.ui.components.SlideToDismissTrack
import com.example.ui.theme.DesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SosAlarmContent(
    callerName: String,
    callerNumber: String,
    missedCount: Int,
    pinRequired: Boolean,
    correctPin: String,
    onStopAlarm: () -> Unit,
    onCallBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val lastCallTime = remember { timeFormat.format(Date()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF450A14), // Deep Red
                        Color(0xFF1E0409),
                        Color(0xFF090103)
                    )
                )
            )
    ) {
        // Concentric pulsing rings (lightweight 2D Canvas)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.32f)
            val baseRadius = size.minDimension * 0.28f

            drawCircle(
                color = DesignTokens.StatusCriticalRed.copy(alpha = ringAlpha),
                radius = baseRadius * ringScale,
                center = center
            )
            drawCircle(
                color = DesignTokens.StatusCriticalRed.copy(alpha = (ringAlpha * 0.7f).coerceIn(0f, 1f)),
                radius = baseRadius * (ringScale * 1.25f),
                center = center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Tag
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DesignTokens.StatusCriticalRed.copy(alpha = 0.3f))
                        .border(1.dp, DesignTokens.StatusCriticalRed, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = DesignTokens.StatusCriticalRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EMERGENCY MISSED CALL ALARM",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Center: Caller Details (WCAG AAA High Contrast White on Dark Red)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                // Bell Icon inside Glowing Circle
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(DesignTokens.StatusCriticalRed)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Emergency Alarm",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Caller Name (32sp Bold)
                Text(
                    text = callerName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Caller Number
                Text(
                    text = callerNumber,
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Call Count (22sp)
                Text(
                    text = "Called $missedCount times repeatedly",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB4AB),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Last Call Time (18sp)
                Text(
                    text = "Last missed call: $lastCallTime",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Actions (Slide to Dismiss + Call Back)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Call Back Button
                GlassButton(
                    onClick = onCallBack,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.alarm_call_back),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Slide To Dismiss Track
                SlideToDismissTrack(
                    onDismissed = {
                        if (pinRequired) {
                            showPinDialog = true
                        } else {
                            onStopAlarm()
                        }
                    },
                    text = stringResource(R.string.alarm_slide_to_stop)
                )

                // Quick Stop Alarm Button
                GlassButton(
                    onClick = {
                        if (pinRequired) {
                            showPinDialog = true
                        } else {
                            onStopAlarm()
                        }
                    },
                    variant = GlassButtonVariant.DESTRUCTIVE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.stop_alarm),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // PIN Verification Modal
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = Color(0xFF1E0409),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = DesignTokens.StatusCriticalRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.alarm_pin_prompt))
                }
            },
            text = {
                Column {
                    GlassTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 4) {
                                enteredPin = it
                                pinError = false
                            }
                        },
                        placeholder = "Enter 4-digit PIN"
                    )
                    if (pinError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.alarm_pin_incorrect),
                            color = DesignTokens.StatusCriticalRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = {
                        if (enteredPin == correctPin) {
                            showPinDialog = false
                            onStopAlarm()
                        } else {
                            pinError = true
                        }
                    },
                    variant = GlassButtonVariant.DESTRUCTIVE
                ) {
                    Text("Dismiss", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassButton(
                    onClick = { showPinDialog = false },
                    variant = GlassButtonVariant.SECONDARY
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
