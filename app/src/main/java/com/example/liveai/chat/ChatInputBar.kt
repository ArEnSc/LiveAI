package com.example.liveai.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single-line text input with a send button that swaps to a push-to-talk
 * mic button when the text field is empty. Shows partial speech transcript
 * as placeholder text while listening.
 */
@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    isListening: Boolean = false,
    partialTranscript: String = "",
    onPressToTalkStart: () -> Unit = {},
    onPressToTalkEnd: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    val hasText = text.isNotBlank()

    fun submit() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            text = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ChatColors.SurfaceDim)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            textStyle = TextStyle(
                color = ChatColors.OnSurface,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(ChatColors.Purple),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = when {
                            isListening && partialTranscript.isNotEmpty() -> partialTranscript
                            isListening -> "Listening..."
                            else -> "Ask anything..."
                        },
                        style = TextStyle(
                            color = if (isListening) ChatColors.Purple else ChatColors.OnSurfaceDim,
                            fontSize = 14.sp
                        )
                    )
                }
                innerTextField()
            }
        )

        // Swap between send button and push-to-talk mic button
        AnimatedContent(
            targetState = hasText,
            transitionSpec = {
                (fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) +
                    scaleIn(initialScale = 0.8f))
                    .togetherWith(
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) +
                            scaleOut(targetScale = 0.8f)
                    )
            },
            label = "inputAction"
        ) { showSend ->
            if (showSend) {
                IconButton(
                    onClick = ::submit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ChatColors.Purple)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = ChatColors.Surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                PushToTalkButton(
                    isListening = isListening,
                    onPressStart = onPressToTalkStart,
                    onPressEnd = onPressToTalkEnd
                )
            }
        }
    }
}
