package com.example.liveai.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Floating circular chat head — Pixel-style white with purple accent icon.
 * Drag is handled at the WindowManager level in ChatOverlayManager.
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
            .clip(CircleShape)
            .background(Color.White),
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
