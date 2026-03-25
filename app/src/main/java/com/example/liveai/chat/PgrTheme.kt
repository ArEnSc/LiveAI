package com.example.liveai.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

/**
 * Punishing: Gray Raven inspired design system.
 * Cold. Angular. Precise. Glowing. Military.
 */
object Pgr {
    // ── Backgrounds (white/light, matching ChatColors) ──
    val Black = Color(0xFFFFFFFF)            // base background = white
    val DarkPanel = Color(0xFFFFFFFF)        // card surface = white
    val Panel = Color(0xFFF7F7FA)            // slightly off-white
    val PanelLight = Color(0xFFF0F0F5)      // light grey
    val Surface = Color(0xFFE8E8EE)          // medium grey

    // ── Primary accent (purple from ChatColors) ──
    val Accent = Color(0xFF7B61FF)
    val AccentLight = Color(0xFFD7AEFB)
    val AccentDim = Color(0xFF7B61FF).copy(alpha = 0.15f)

    // ── Status accents ──
    val Cyan = Color(0xFF7B61FF)             // use purple as primary active
    val Amber = Color(0xFFE8A317)            // warm amber
    val Red = Color(0xFFE53935)              // error red
    val Green = Color(0xFF43A047)            // success green
    val Purple = Color(0xFF7B61FF)
    val Muted = Color(0xFF9E9E9E)

    // ── Status card backgrounds (subtle tinted white panels) ──
    val BgRunning = Color(0xFFF3F0FF)        // purple-tinted white
    val BgQueued = Color(0xFFF5F5F7)         // neutral light
    val BgSuspended = Color(0xFFFFF8E8)      // warm amber-tinted
    val BgCompleted = Color(0xFFEDF7EE)      // green-tinted white
    val BgFailed = Color(0xFFFDECEC)         // red-tinted white
    val BgCancelled = Color(0xFFF2F2F4)      // grey-tinted

    // ── Tool call backgrounds ──
    val BgToolActive = Color(0xFFF0ECFF)     // purple-tinted for running
    val BgToolDone = Color(0xFFEBF5EB)       // green-tinted for done
    val BgToolError = Color(0xFFFCEBEB)      // red-tinted for error

    // ── Text (dark on light, matching ChatColors) ──
    val TextPrimary = Color(0xFF1F1F1F)
    val TextSecondary = Color(0xFF5F6368)
    val TextTertiary = Color(0xFF9AA0A6)
    val TextCyan = Accent
}

/**
 * Chamfered rectangle shape — signature PGR angular cut.
 * Cuts the top-right and bottom-left corners at 45 degrees.
 */
class ChamferedShape(private val cutSize: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): androidx.compose.ui.graphics.Outline {
        val cut = with(density) { cutSize.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height)
            lineTo(cut, size.height)
            lineTo(0f, size.height - cut)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/**
 * Draw corner bracket decorations — small L-shaped markers at corners.
 */
fun DrawScope.drawCornerBrackets(
    color: Color,
    armLength: Float = 10f,
    strokeWidth: Float = 1f,
    inset: Float = 0f
) {
    val w = size.width
    val h = size.height

    // Top-left
    drawLine(color, Offset(inset, inset), Offset(inset + armLength, inset), strokeWidth)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + armLength), strokeWidth)

    // Top-right
    drawLine(color, Offset(w - inset, inset), Offset(w - inset - armLength, inset), strokeWidth)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + armLength), strokeWidth)

    // Bottom-left
    drawLine(color, Offset(inset, h - inset), Offset(inset + armLength, h - inset), strokeWidth)
    drawLine(color, Offset(inset, h - inset), Offset(inset, h - inset - armLength), strokeWidth)

    // Bottom-right
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset - armLength, h - inset), strokeWidth)
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset, h - inset - armLength), strokeWidth)
}

/**
 * Draw chamfered border outline matching [ChamferedShape].
 */
fun DrawScope.drawChamferedBorder(
    color: Color,
    cutSize: Float,
    strokeWidth: Float = 1.5f
) {
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width - cutSize, 0f)
        lineTo(size.width, cutSize)
        lineTo(size.width, size.height)
        lineTo(cutSize, size.height)
        lineTo(0f, size.height - cutSize)
        close()
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

/**
 * Modifier that draws a slow-scrolling scan line across the component.
 */
@Composable
fun Modifier.scanLine(
    color: Color = Pgr.Accent.copy(alpha = 0.06f),
    durationMs: Int = 4000
): Modifier {
    val transition = rememberInfiniteTransition(label = "scanLine")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLineProgress"
    )

    return this.drawWithContent {
        drawContent()
        val y = size.height * progress
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }
}
