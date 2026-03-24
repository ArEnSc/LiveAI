package com.example.liveai.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * Transparent chat panel with clip-mask reveal animation.
 * Content is always full-size but clipped by an animated width.
 */
@Composable
fun ChatPanel(
    visible: Boolean,
    messages: List<ChatMessage> = emptyList(),
    mode: ChatMode = ChatMode.Short,
    onModeChange: (ChatMode) -> Unit = {},
    onSend: (String) -> Unit = {},
    onCollapseFinished: () -> Unit = {},
    speechState: SpeechState = SpeechState.Idle,
    onPressToTalkStart: () -> Unit = {},
    onPressToTalkEnd: () -> Unit = {}
) {
    val revealFraction = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            revealFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        } else {
            revealFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
            onCollapseFinished()
        }
    }

    // Reorder for short mode: assistant on top, user on bottom (near input)
    val displayMessages = if (mode == ChatMode.Short) {
        val assistantMessages = messages.filter { !it.isUser }
        val userMessages = messages.filter { it.isUser }
        assistantMessages + userMessages
    } else {
        messages
    }

    // Clip-mask reveal: full-size box, clipped from left edge by animated fraction
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .drawWithContent {
                val clipWidth = size.width * revealFraction.value
                clipPath(
                    Path().apply {
                        addRect(Rect(0f, 0f, clipWidth, size.height))
                    }
                ) {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // History button with padding so it doesn't clip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                HistoryButton(
                    isHistoryMode = mode == ChatMode.History,
                    onClick = {
                        onModeChange(
                            if (mode == ChatMode.History) ChatMode.Short else ChatMode.History
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Messages with padding for shadow overflow
            val listState = rememberLazyListState()

            LaunchedEffect(displayMessages.size, displayMessages.lastOrNull()?.text) {
                if (displayMessages.isNotEmpty()) {
                    listState.animateScrollToItem(displayMessages.lastIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
            ) {
                items(displayMessages) { message ->
                    MessageBubble(message = message)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input bar — inset to align with message bubbles (4dp panel + 10dp pointer)
            Box(modifier = Modifier.padding(start = 14.dp, end = 4.dp)) {
                ChatInputBar(
                    onSend = onSend,
                    isListening = speechState is SpeechState.Listening,
                    partialTranscript = (speechState as? SpeechState.Listening)?.partialText.orEmpty(),
                    onPressToTalkStart = onPressToTalkStart,
                    onPressToTalkEnd = onPressToTalkEnd
                )
            }
        }
    }
}
