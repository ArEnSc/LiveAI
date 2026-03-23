package com.example.liveai.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Chat panel container that mask-reveals via animated scaleX.
 * Shows message history in a scrollable list with input bar at bottom.
 */
@Composable
fun ChatPanel(
    visible: Boolean,
    messages: List<ChatMessage> = emptyList(),
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
            .clip(RoundedCornerShape(16.dp))
            .background(ChatColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(12.dp)
        ) {
            // Messages list — takes remaining space
            val listState = rememberLazyListState()

            // Auto-scroll to bottom when messages change
            LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input bar pinned to bottom
            ChatInputBar(onSend = onSend)
        }
    }
}
