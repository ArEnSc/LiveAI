package com.example.liveai.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Transparent chat panel that mask-reveals via animated scaleX.
 * Messages float on transparent background. Input bar at the bottom
 * aligns vertically with the floating ball. History button above messages.
 */
@Composable
fun ChatPanel(
    visible: Boolean,
    messages: List<ChatMessage> = emptyList(),
    mode: ChatMode = ChatMode.Short,
    onModeChange: (ChatMode) -> Unit = {},
    onSend: (String) -> Unit = {},
    onCollapseFinished: () -> Unit = {}
) {
    val scaleX = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            scaleX.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        } else {
            scaleX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250)
            )
            onCollapseFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                this.scaleX = scaleX.value
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // History button above messages
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                HistoryButton(
                    isHistoryMode = mode == ChatMode.History,
                    onClick = {
                        val newMode = if (mode == ChatMode.History) ChatMode.Short else ChatMode.Short
                        onModeChange(
                            if (mode == ChatMode.History) ChatMode.Short else ChatMode.History
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Messages list
            val listState = rememberLazyListState()

            LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input bar at the bottom — aligns with the ball vertically
            ChatInputBar(onSend = onSend)
        }
    }
}
