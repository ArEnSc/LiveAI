package com.example.liveai.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Floating circular chat head — Pixel-style white with purple accent icon.
 * Uses a manual circular shadow via blur paint since Compose shadow
 * doesn't render correctly on translucent overlay windows.
 */
@Composable
fun ChatTab(
    isExpanded: Boolean
) {
    val iconRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "iconRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .circularShadow(
                    color = Color.Black.copy(alpha = 0.2f),
                    blurRadius = 12.dp
                )
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.ChatBubbleOutline,
                contentDescription = if (isExpanded) "Close chat" else "Open chat",
                tint = ChatColors.Purple,
                modifier = Modifier.graphicsLayer {
                    rotationZ = iconRotation
                }
            )
        }
    }
}

/**
 * Draws a circular blur shadow behind the composable using Android's native
 * Paint blur filter. Works on translucent overlay windows where Compose's
 * built-in shadow does not render correctly.
 */
private fun Modifier.circularShadow(
    color: Color,
    blurRadius: androidx.compose.ui.unit.Dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().also {
            val frameworkPaint = it.asFrameworkPaint()
            frameworkPaint.color = color.toArgb()
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                blurRadius.toPx(),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        val radius = size.minDimension / 2f
        canvas.drawCircle(
            center = center,
            radius = radius,
            paint = paint
        )
    }
}
