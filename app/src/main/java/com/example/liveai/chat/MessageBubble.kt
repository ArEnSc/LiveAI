package com.example.liveai.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val POINTER_WIDTH = 10.dp
private val POINTER_HEIGHT = 16.dp

/**
 * Angular speech bubble with shadow all around.
 * Assistant: purple, visible left pointer.
 * User: white, invisible pointer (transparent, same size for alignment).
 */
@Composable
fun MessageBubble(
    message: ChatMessage
) {
    val bubbleColor = if (message.isUser) ChatColors.Surface else ChatColors.Purple
    val textColor = if (message.isUser) ChatColors.OnSurface else ChatColors.Surface
    val shape = RoundedCornerShape(4.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Both get a pointer slot — assistant visible, user transparent (for alignment)
        PointerTail(
            color = if (message.isUser) Color.Transparent else bubbleColor
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .rectShadow(
                    color = Color.Black.copy(alpha = 0.15f),
                    blurRadius = 6.dp,
                    offsetY = 1.dp
                )
                .background(bubbleColor, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (message.isStreaming) {
                StreamingText(text = message.text, textColor = textColor)
            } else {
                Text(
                    text = message.text,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

/**
 * Triangular pointer tail pointing left, vertically centered on the bubble.
 */
@Composable
private fun PointerTail(
    color: Color
) {
    Canvas(
        modifier = Modifier.size(width = POINTER_WIDTH, height = POINTER_HEIGHT)
    ) {
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(0f, size.height / 2f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun StreamingText(
    text: String,
    textColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    val annotated = buildAnnotatedString {
        append(text)
        pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = textColor.copy(alpha = cursorAlpha)
            )
        )
        append("\u2588")
        pop()
    }

    Text(
        text = annotated,
        style = TextStyle(
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    )
}

/**
 * Draws a rectangular blur shadow all around the composable using Android's
 * native Paint blur filter. Works on translucent overlay windows.
 */
private fun Modifier.rectShadow(
    color: Color,
    blurRadius: Dp,
    offsetY: Dp = 0.dp
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
        canvas.drawRect(
            left = 0f,
            top = offsetY.toPx(),
            right = size.width,
            bottom = size.height + offsetY.toPx(),
            paint = paint
        )
    }
}
