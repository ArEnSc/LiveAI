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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface ToolCallStatus {
    data object InProgress : ToolCallStatus
    data class Complete(val durationMs: Long) : ToolCallStatus
    data class Error(val message: String) : ToolCallStatus
}

data class ToolCallDisplay(
    val toolName: String,
    val status: ToolCallStatus,
    val resultPreview: String? = null
)

@Composable
fun ToolCallBubble(
    display: ToolCallDisplay,
    modifier: Modifier = Modifier
) {
    val accentColor = when (display.status) {
        is ToolCallStatus.InProgress -> Pgr.Cyan
        is ToolCallStatus.Complete -> Pgr.Green
        is ToolCallStatus.Error -> Pgr.Red
    }
    val (fillLight, fillDark) = when (display.status) {
        is ToolCallStatus.InProgress -> Pgr.BgToolActive to Pgr.BgToolActiveDark
        is ToolCallStatus.Complete -> Pgr.BgToolDone to Pgr.BgToolDoneDark
        is ToolCallStatus.Error -> Pgr.BgToolError to Pgr.BgToolErrorDark
    }
    val cutDp = 6.dp
    val shape = ChamferedShape(cutDp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                drawPgrCard(fillLight, fillDark, cutSize = cutDp.toPx())
            }
            .then(if (display.status is ToolCallStatus.InProgress) Modifier.scanLine() else Modifier)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(18.dp)
                    ,
                contentAlignment = Alignment.Center
            ) {
                when (display.status) {
                    is ToolCallStatus.InProgress -> {
                        val transition = rememberInfiniteTransition(label = "toolSpin")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "toolSpinAnim"
                        )
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = "Running",
                            tint = Color.White,
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }
                    is ToolCallStatus.Complete -> {
                        Icon(Icons.Rounded.Check, "Complete", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    is ToolCallStatus.Error -> {
                        Icon(Icons.Rounded.Close, "Error", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Tool name + duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = display.toolName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    if (display.status is ToolCallStatus.Complete) {
                        Text(
                            text = formatDuration(display.status.durationMs),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // Status line
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (display.status) {
                        is ToolCallStatus.InProgress -> "EXECUTING..."
                        is ToolCallStatus.Complete -> "COMPLETE"
                        is ToolCallStatus.Error -> display.status.message
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )

                // Progress bar for in-progress
                if (display.status is ToolCallStatus.InProgress) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedProgressBar(
                        segments = 12,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Result preview
                if (display.resultPreview != null && display.status is ToolCallStatus.Complete) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = display.resultPreview,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * PGR-style segmented progress bar — discrete blocks that fill with animated sweep.
 */
@Composable
private fun SegmentedProgressBar(
    segments: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "segProg")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = segments.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "segSweep"
    )

    Row(
        modifier = modifier.height(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until segments) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        if (i < sweep.toInt()) color.copy(alpha = 0.8f)
                        else color.copy(alpha = 0.1f)
                    )
            )
        }
    }
}

/**
 * Tool call steps with connecting lines — PGR tactical readout style.
 */
@Composable
fun ToolCallSteps(
    steps: List<ToolCallDisplay>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        steps.forEachIndexed { index, step ->
            val accentColor = when (step.status) {
                is ToolCallStatus.Complete -> Pgr.Green
                is ToolCallStatus.Error -> Pgr.Red
                is ToolCallStatus.InProgress -> Pgr.Cyan
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Connector
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(16.dp)
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(6.dp)
                                .background(accentColor.copy(alpha = 0.3f))
                        )
                    }
                    // Diamond dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(accentColor)
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(6.dp)
                                .background(Pgr.Muted.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                ToolCallBubble(
                    display = step,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    return if (ms < 1000) "${ms}ms" else "${"%.1f".format(ms / 1000.0)}s"
}
