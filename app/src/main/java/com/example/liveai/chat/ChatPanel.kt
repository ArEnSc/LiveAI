package com.example.liveai.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Chat panel container that mask-reveals via animated scaleX.
 * Automatically expands on first composition, collapses when [visible] becomes false.
 * Calls [onCollapseFinished] when the collapse animation completes so the manager
 * can remove the window.
 */
@Composable
fun ChatPanel(
    visible: Boolean,
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
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(ChatColors.Surface)
    )
}
