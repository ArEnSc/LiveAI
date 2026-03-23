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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Angular speech bubble with a triangular pointer tail.
 * Assistant messages: pointer on the left.
 * User messages: pointer on the right, purple accent.
 */
@Composable
fun MessageBubble(
    message: ChatMessage
) {
    val bubbleColor = if (message.isUser) ChatColors.Purple else ChatColors.Surface
    val textColor = if (message.isUser) ChatColors.Surface else ChatColors.OnSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            // Left pointer for assistant
            PointerTail(color = bubbleColor, pointLeft = true)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
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

        if (message.isUser) {
            // Right pointer for user
            PointerTail(color = bubbleColor, pointLeft = false)
        }
    }
}

/**
 * Triangular pointer tail drawn with Canvas.
 * [pointLeft] = true means the triangle points left (assistant).
 */
@Composable
private fun PointerTail(
    color: Color,
    pointLeft: Boolean
) {
    Canvas(
        modifier = Modifier.size(width = 10.dp, height = 16.dp)
    ) {
        val path = Path().apply {
            if (pointLeft) {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            }
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
                color = ChatColors.Purple.copy(alpha = cursorAlpha)
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
