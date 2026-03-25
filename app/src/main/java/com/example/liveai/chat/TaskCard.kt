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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liveai.agent.model.BackgroundTask
import com.example.liveai.agent.model.TaskResult
import com.example.liveai.agent.model.TaskStatus

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
        TaskStatus.QUEUED -> Pgr.Muted
        TaskStatus.RUNNING -> Pgr.Cyan
        TaskStatus.SUSPENDED -> Pgr.Amber
        TaskStatus.COMPLETED -> Pgr.Green
        TaskStatus.FAILED -> Pgr.Red
        TaskStatus.CANCELLED -> Pgr.Muted
    }
    val bgColor = when (task.status) {
        TaskStatus.QUEUED -> Pgr.BgQueued
        TaskStatus.RUNNING -> Pgr.BgRunning
        TaskStatus.SUSPENDED -> Pgr.BgSuspended
        TaskStatus.COMPLETED -> Pgr.BgCompleted
        TaskStatus.FAILED -> Pgr.BgFailed
        TaskStatus.CANCELLED -> Pgr.BgCancelled
    }
    val cutDp = 8.dp
    val shape = ChamferedShape(cutDp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .drawBehind {
                drawChamferedBorder(statusColor.copy(alpha = 0.25f), cutDp.toPx(), 0.8f)
                drawCornerBrackets(statusColor.copy(alpha = 0.15f), armLength = 7f, strokeWidth = 0.8f, inset = 2f)
            }
            .then(if (task.status == TaskStatus.RUNNING) Modifier.scanLine() else Modifier)
            .padding(10.dp)
    ) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Status indicator — diamond shape
            TaskStatusDiamond(status = task.status, color = statusColor)

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.instructions,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Pgr.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        text = task.status.name,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        letterSpacing = 1.sp
                    )
                    if (task.status == TaskStatus.RUNNING && task.progress.percent != null) {
                        Text(
                            text = " / ${(task.progress.percent * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Actions
            TaskActions(task.status, onPause, onResume, onCancel, onClear, statusColor)
        }

        // Progress bar for RUNNING
        if (task.status == TaskStatus.RUNNING) {
            Spacer(modifier = Modifier.height(10.dp))

            if (task.progress.percent != null) {
                PgrProgressBar(
                    progress = task.progress.percent,
                    color = Pgr.Cyan,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PgrIndeterminateBar(
                    color = Pgr.Cyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (task.progress.phase.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(task.progress.phase.uppercase())
                        if (task.progress.detail != null) {
                            append(" // ")
                            append(task.progress.detail)
                        }
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Pgr.TextTertiary,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Suspended
        if (task.status == TaskStatus.SUSPENDED) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ">> PAUSED — AWAITING RESUME",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Pgr.Amber.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp
            )
        }

        // Completed result
        if (task.status == TaskStatus.COMPLETED && task.result is TaskResult.Success) {
            val success = task.result as TaskResult.Success
            Spacer(modifier = Modifier.height(8.dp))
            // Thin separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Pgr.Green.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = success.summary,
                fontSize = 11.sp,
                color = Pgr.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Text(
                text = "${"%.1f".format(success.durationMs / 1000.0)}s",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Pgr.TextTertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Failed error
        if (task.status == TaskStatus.FAILED && task.result is TaskResult.Failure) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ERR: ${(task.result as TaskResult.Failure).error}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Pgr.Red.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskStatusDiamond(status: TaskStatus, color: Color) {
    val pulsing = status == TaskStatus.RUNNING
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val a by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        a
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .graphicsLayer {
                rotationZ = 45f
                this.alpha = alpha
            }
            .background(color)
    )
}

/**
 * PGR-style segmented progress bar — angular, discrete blocks.
 */
@Composable
private fun PgrProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 20
) {
    val filledCount = (progress * segments).toInt()

    Row(
        modifier = modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        for (i in 0 until segments) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        if (i < filledCount) color
                        else color.copy(alpha = 0.08f)
                    )
            )
        }
    }
}

/**
 * PGR-style indeterminate bar — sweeping segments.
 */
@Composable
private fun PgrIndeterminateBar(
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 20
) {
    val transition = rememberInfiniteTransition(label = "indet")
    val sweep by transition.animateFloat(
        initialValue = -4f,
        targetValue = segments.toFloat() + 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indetSweep"
    )

    Row(
        modifier = modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        for (i in 0 until segments) {
            val distance = kotlin.math.abs(i - sweep)
            val alpha = (1f - (distance / 4f)).coerceIn(0f, 1f) * 0.8f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color.copy(alpha = alpha.coerceAtLeast(0.08f)))
            )
        }
    }
}

@Composable
private fun TaskActions(
    status: TaskStatus,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onClear: (() -> Unit)?,
    color: Color
) {
    Row {
        when (status) {
            TaskStatus.RUNNING -> {
                if (onPause != null) ActionBtn(Icons.Rounded.Pause, "Pause", Pgr.Amber, onPause)
                if (onCancel != null) ActionBtn(Icons.Rounded.Close, "Cancel", Pgr.Red, onCancel)
            }
            TaskStatus.SUSPENDED -> {
                if (onResume != null) ActionBtn(Icons.Rounded.PlayArrow, "Resume", Pgr.Cyan, onResume)
                if (onCancel != null) ActionBtn(Icons.Rounded.Close, "Cancel", Pgr.Red, onCancel)
            }
            TaskStatus.QUEUED -> {
                if (onCancel != null) ActionBtn(Icons.Rounded.Close, "Cancel", Pgr.Red, onCancel)
            }
            else -> {
                if (onClear != null) ActionBtn(Icons.Rounded.Cancel, "Clear", Pgr.TextTertiary, onClear)
            }
        }
    }
}

@Composable
private fun ActionBtn(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(icon, label, tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}
