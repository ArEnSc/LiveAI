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
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liveai.agent.model.BackgroundTask
import com.example.liveai.agent.model.TaskResult
import com.example.liveai.agent.model.TaskStatus

private val TaskCardBg = Color(0xFF2A2A3E)
private val TaskText = Color(0xFFB8B8D0)
private val TaskTextDim = Color(0xFF6B6B8A)
private val QueuedColor = Color(0xFF90A4AE)
private val RunningColor = Color(0xFF7B61FF)
private val SuspendedColor = Color(0xFFFFA726)
private val CompletedColor = Color(0xFF4CAF50)
private val FailedColor = Color(0xFFEF5350)
private val CancelledColor = Color(0xFF78909C)

@Composable
fun TaskCard(
    task: BackgroundTask,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusColor = when (task.status) {
        TaskStatus.QUEUED -> QueuedColor
        TaskStatus.RUNNING -> RunningColor
        TaskStatus.SUSPENDED -> SuspendedColor
        TaskStatus.COMPLETED -> CompletedColor
        TaskStatus.FAILED -> FailedColor
        TaskStatus.CANCELLED -> CancelledColor
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TaskCardBg)
            .padding(12.dp)
    ) {
        // Header: status icon + instructions + actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Status icon
            TaskStatusIcon(status = task.status, color = statusColor)

            Spacer(modifier = Modifier.width(10.dp))

            // Task name + status label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.instructions,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = statusColor
                )
            }

            // Action buttons
            TaskActions(
                status = task.status,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onClear = onClear
            )
        }

        // Progress bar for RUNNING
        if (task.status == TaskStatus.RUNNING) {
            Spacer(modifier = Modifier.height(10.dp))

            if (task.progress.percent != null) {
                // Determinate
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { task.progress.percent },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = RunningColor,
                        trackColor = RunningColor.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(task.progress.percent * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RunningColor
                    )
                }
            } else {
                // Indeterminate
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = RunningColor,
                    trackColor = RunningColor.copy(alpha = 0.15f)
                )
            }

            // Phase + detail
            if (task.progress.phase.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Text(
                        text = task.progress.phase,
                        fontSize = 11.sp,
                        color = TaskTextDim
                    )
                    if (task.progress.detail != null) {
                        Text(
                            text = " — ${task.progress.detail}",
                            fontSize = 11.sp,
                            color = TaskTextDim.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Suspended indicator
        if (task.status == TaskStatus.SUSPENDED) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(SuspendedColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Paused — tap resume to continue",
                    fontSize = 11.sp,
                    color = SuspendedColor.copy(alpha = 0.7f)
                )
            }
        }

        // Result preview for COMPLETED
        if (task.status == TaskStatus.COMPLETED && task.result is TaskResult.Success) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (task.result as TaskResult.Success).summary,
                fontSize = 11.sp,
                color = TaskText.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            val duration = (task.result as TaskResult.Success).durationMs
            Text(
                text = "Completed in ${"%.1f".format(duration / 1000.0)}s",
                fontSize = 10.sp,
                color = TaskTextDim.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Error for FAILED
        if (task.status == TaskStatus.FAILED && task.result is TaskResult.Failure) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (task.result as TaskResult.Failure).error,
                fontSize = 11.sp,
                color = FailedColor.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskStatusIcon(status: TaskStatus, color: Color) {
    val icon: ImageVector
    val spinning: Boolean

    when (status) {
        TaskStatus.QUEUED -> { icon = Icons.Rounded.HourglassBottom; spinning = false }
        TaskStatus.RUNNING -> { icon = Icons.Rounded.Refresh; spinning = true }
        TaskStatus.SUSPENDED -> { icon = Icons.Rounded.Pause; spinning = false }
        TaskStatus.COMPLETED -> { icon = Icons.Rounded.Check; spinning = false }
        TaskStatus.FAILED -> { icon = Icons.Rounded.Close; spinning = false }
        TaskStatus.CANCELLED -> { icon = Icons.Rounded.Cancel; spinning = false }
    }

    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition(label = "taskSpin")
        val r by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "taskSpinAnim"
        )
        r
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status.name,
            tint = color,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
private fun TaskActions(
    status: TaskStatus,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onClear: (() -> Unit)?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        when (status) {
            TaskStatus.RUNNING -> {
                if (onPause != null) {
                    SmallActionButton(Icons.Rounded.Pause, "Pause", SuspendedColor, onPause)
                }
                if (onCancel != null) {
                    SmallActionButton(Icons.Rounded.Close, "Cancel", FailedColor, onCancel)
                }
            }
            TaskStatus.SUSPENDED -> {
                if (onResume != null) {
                    SmallActionButton(Icons.Rounded.PlayArrow, "Resume", RunningColor, onResume)
                }
                if (onCancel != null) {
                    SmallActionButton(Icons.Rounded.Close, "Cancel", FailedColor, onCancel)
                }
            }
            TaskStatus.QUEUED -> {
                if (onCancel != null) {
                    SmallActionButton(Icons.Rounded.Close, "Cancel", FailedColor, onCancel)
                }
            }
            TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED -> {
                if (onClear != null) {
                    SmallActionButton(Icons.Rounded.Close, "Clear", CancelledColor, onClear)
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}
