package com.example.liveai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Small circular button to toggle between Short and History mode.
 * Shows history icon when in short mode, chat icon when in history mode.
 */
@Composable
fun HistoryButton(
    isHistoryMode: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ChatColors.Surface)
    ) {
        Icon(
            imageVector = if (isHistoryMode) Icons.Rounded.ChatBubbleOutline else Icons.Rounded.History,
            contentDescription = if (isHistoryMode) "Short mode" else "Show history",
            tint = ChatColors.Purple,
            modifier = Modifier.size(20.dp)
        )
    }
}
