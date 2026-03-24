package com.example.liveai.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val BeamPink = Color(0xFFFF6B9D)
private val BeamPurple = Color(0xFF7B61FF)
private val BeamTeal = Color(0xFF4ECDC4)

/**
 * Circular mic button with an animated pink→purple→teal gradient border beam.
 * Press-and-hold to record; release to stop.
 */
@Composable
fun PushToTalkButton(
    isListening: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Infinite rotation for the beam
    val infiniteTransition = rememberInfiniteTransition(label = "beam")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 1000 else 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "beamRotation"
    )

    // Beam opacity: subtle when idle, full when listening
    val beamAlpha by animateFloatAsState(
        targetValue = if (isListening) 1f else 0.6f,
        animationSpec = tween(300),
        label = "beamAlpha"
    )

    // Scale up slightly when pressed
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = tween(150),
        label = "buttonScale"
    )

    // Stroke width: thicker when listening
    val strokeWidth = if (isListening) 3.5f else 2.5f

    val beamBrush = Brush.sweepGradient(
        0.0f to BeamPink,
        0.25f to BeamPurple,
        0.5f to BeamTeal,
        0.7f to Color.Transparent,
        1.0f to BeamPink
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .scale(scale)
            .drawBehind {
                rotate(rotation, pivot = Offset(size.width / 2, size.height / 2)) {
                    drawCircle(
                        brush = beamBrush,
                        radius = size.minDimension / 2,
                        alpha = beamAlpha,
                        style = Stroke(width = strokeWidth.dp.toPx())
                    )
                }
            }
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        tryAwaitRelease()
                        onPressEnd()
                    }
                )
            }
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = "Push to talk",
            tint = if (isListening) BeamPurple else ChatColors.OnSurfaceDim,
            modifier = Modifier.size(18.dp)
        )
    }
}
