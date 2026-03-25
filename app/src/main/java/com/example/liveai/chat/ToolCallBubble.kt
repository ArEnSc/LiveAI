package com.example.liveai.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Status of a tool call within the agent loop.
 */
sealed interface ToolCallStatus {
    data object InProgress : ToolCallStatus
    data class Complete(val durationMs: Long) : ToolCallStatus
    data class Error(val message: String) : ToolCallStatus
}

/**
 * Display data for a tool call bubble in the chat.
 */
data class ToolCallDisplay(
    val toolName: String,
    val status: ToolCallStatus,
    val resultPreview: String? = null
)

private val ToolBg = Color(0xFF2A2A3E)
private val ToolBorder = Color(0xFF3A3A5E)
private val ToolText = Color(0xFFB8B8D0)
private val SuccessGreen = Color(0xFF4CAF50)
private val ErrorRed = Color(0xFFEF5350)
private val InProgressBlue = Color(0xFF7B61FF)

/**
 * Chat bubble showing a tool call — spinner while running, checkmark when done,
 * X on error. Shows tool name, duration, and result preview.
 */
@Composable
fun ToolCallBubble(
    display: ToolCallDisplay,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ToolBg)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Status icon
        StatusIcon(display.status)

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Tool name + duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = display.toolName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = when (display.status) {
                        is ToolCallStatus.InProgress -> InProgressBlue
                        is ToolCallStatus.Complete -> SuccessGreen
                        is ToolCallStatus.Error -> ErrorRed
                    }
                )

                if (display.status is ToolCallStatus.Complete) {
                    Text(
                        text = formatDuration(display.status.durationMs),
                        fontSize = 11.sp,
                        color = ToolText.copy(alpha = 0.6f)
                    )
                }
            }

            // Status text
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = when (display.status) {
                    is ToolCallStatus.InProgress -> "Running..."
                    is ToolCallStatus.Complete -> "Complete"
                    is ToolCallStatus.Error -> display.status.message
                },
                fontSize = 11.sp,
                color = when (display.status) {
                    is ToolCallStatus.Error -> ErrorRed.copy(alpha = 0.8f)
                    else -> ToolText.copy(alpha = 0.5f)
                }
            )

            // Progress bar for in-progress
            if (display.status is ToolCallStatus.InProgress) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = InProgressBlue,
                    trackColor = ToolBorder
                )
            }

            // Result preview
            if (display.resultPreview != null && display.status is ToolCallStatus.Complete) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = display.resultPreview,
                    fontSize = 11.sp,
                    color = ToolText.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(status: ToolCallStatus) {
    val size = 20.dp
    when (status) {
        is ToolCallStatus.InProgress -> {
            val transition = rememberInfiniteTransition(label = "spin")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "toolSpin"
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(InProgressBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Build,
                    contentDescription = "Running",
                    tint = InProgressBlue,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }

        is ToolCallStatus.Complete -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Complete",
                    tint = SuccessGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        is ToolCallStatus.Error -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(ErrorRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Error",
                    tint = ErrorRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    return if (ms < 1000) "${ms}ms" else "${"%.1f".format(ms / 1000.0)}s"
}

/**
 * Shows a sequence of tool calls as a vertical step list with connecting lines.
 */
@Composable
fun ToolCallSteps(
    steps: List<ToolCallDisplay>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                // Step connector line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(20.dp)
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(6.dp)
                                .background(
                                    when (step.status) {
                                        is ToolCallStatus.Complete -> SuccessGreen.copy(alpha = 0.3f)
                                        is ToolCallStatus.Error -> ErrorRed.copy(alpha = 0.3f)
                                        is ToolCallStatus.InProgress -> InProgressBlue.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                    // Step dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (step.status) {
                                    is ToolCallStatus.Complete -> SuccessGreen
                                    is ToolCallStatus.Error -> ErrorRed
                                    is ToolCallStatus.InProgress -> InProgressBlue
                                }
                            )
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(6.dp)
                                .background(
                                    when (steps[index + 1].status) {
                                        is ToolCallStatus.Complete -> SuccessGreen.copy(alpha = 0.3f)
                                        is ToolCallStatus.Error -> ErrorRed.copy(alpha = 0.3f)
                                        is ToolCallStatus.InProgress -> InProgressBlue.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                ToolCallBubble(
                    display = step,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
