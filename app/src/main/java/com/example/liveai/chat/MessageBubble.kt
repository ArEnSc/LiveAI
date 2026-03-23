package com.example.liveai.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single chat message bubble. User messages align right with purple background,
 * assistant messages align left with light grey. Shows a blinking cursor when streaming.
 */
@Composable
fun MessageBubble(
    message: ChatMessage
) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isUser) ChatColors.Purple else ChatColors.SurfaceDim
    val textColor = if (message.isUser) ChatColors.Surface else ChatColors.OnSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (message.isUser) 0.85f else 0.95f)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (message.isUser) 12.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 12.dp
                    )
                )
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (message.isStreaming) {
                StreamingText(
                    text = message.text,
                    textColor = textColor
                )
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

@Composable
private fun StreamingText(
    text: String,
    textColor: androidx.compose.ui.graphics.Color
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
        // Blinking block cursor
        pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = ChatColors.Purple.copy(alpha = cursorAlpha)
            )
        )
        append("█")
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
