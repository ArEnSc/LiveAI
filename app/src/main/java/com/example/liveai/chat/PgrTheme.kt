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
    // ── Backgrounds ──
    val Black = Color(0xFF0A0A0F)
    val DarkPanel = Color(0xFF0F1018)
    val Panel = Color(0xFF161822)
    val PanelLight = Color(0xFF1E2130)
    val Surface = Color(0xFF282C3E)

    // ── Status accents ──
    val Cyan = Color(0xFF00D4FF)
    val Amber = Color(0xFFFFA726)
    val Red = Color(0xFFFF5252)
    val Green = Color(0xFF69F0AE)
    val Purple = Color(0xFF7C4DFF)
    val Muted = Color(0xFF546E7A)

    // ── Status card backgrounds (subtle tinted panels) ──
    val BgRunning = Color(0xFF0D1820)       // cyan-tinted dark
    val BgQueued = Color(0xFF111318)         // neutral dark
    val BgSuspended = Color(0xFF1A1508)     // warm amber-tinted dark
    val BgCompleted = Color(0xFF0A1A10)     // green-tinted dark
    val BgFailed = Color(0xFF1A0C0C)        // red-tinted dark
    val BgCancelled = Color(0xFF101214)     // cold grey dark

    // ── Tool call backgrounds ──
    val BgToolActive = Color(0xFF0C1520)    // cyan-tinted for running tool
    val BgToolDone = Color(0xFF0C1810)      // green-tinted for completed tool
    val BgToolError = Color(0xFF180C0C)     // red-tinted for errored tool

    // ── Text ──
    val TextPrimary = Color(0xFFE8EAF0)
    val TextSecondary = Color(0xFF7E8494)
    val TextTertiary = Color(0xFF454B5C)
    val TextCyan = Cyan
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
    color: Color = Pgr.Cyan.copy(alpha = 0.08f),
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
