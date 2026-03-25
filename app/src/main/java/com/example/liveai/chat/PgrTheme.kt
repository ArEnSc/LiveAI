package com.example.liveai.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
 * Angular cards with layered borders, top-edge highlights,
 * corner vertex accents, tech lines, and gradient fills.
 */
object Pgr {
    // ── Backgrounds ──
    val Black = Color(0xFFFFFFFF)
    val DarkPanel = Color(0xFFFFFFFF)
    val Panel = Color(0xFFF7F7FA)
    val PanelLight = Color(0xFFF0F0F5)
    val Surface = Color(0xFFE8E8EE)

    // ── Primary accent ──
    val Accent = Color(0xFF7B61FF)
    val AccentLight = Color(0xFFD7AEFB)
    val AccentDim = Color(0xFF7B61FF).copy(alpha = 0.15f)

    // ── Status accents ──
    val Cyan = Color(0xFF7B61FF)
    val Amber = Color(0xFFE8A317)
    val Red = Color(0xFFE53935)
    val Green = Color(0xFF43A047)
    val Purple = Color(0xFF7B61FF)
    val Muted = Color(0xFF9E9E9E)

    // ── Status card fills (solid base, gradient applied on top) ──
    val BgRunning = Color(0xFF7B61FF)
    val BgRunningDark = Color(0xFF5A43D9)
    val BgQueued = Color(0xFF78909C)
    val BgQueuedDark = Color(0xFF546E7A)
    val BgSuspended = Color(0xFFE8A317)
    val BgSuspendedDark = Color(0xFFC68A00)
    val BgCompleted = Color(0xFF43A047)
    val BgCompletedDark = Color(0xFF2E7D32)
    val BgFailed = Color(0xFFE53935)
    val BgFailedDark = Color(0xFFC62828)
    val BgCancelled = Color(0xFF757575)
    val BgCancelledDark = Color(0xFF616161)

    // ── Tool call fills ──
    val BgToolActive = Color(0xFF7B61FF)
    val BgToolActiveDark = Color(0xFF5A43D9)
    val BgToolDone = Color(0xFF43A047)
    val BgToolDoneDark = Color(0xFF2E7D32)
    val BgToolError = Color(0xFFE53935)
    val BgToolErrorDark = Color(0xFFC62828)

    // ── Text on solid cards ──
    val OnCard = Color(0xFFFFFFFF)
    val OnCardDim = Color(0xFFFFFFFF).copy(alpha = 0.7f)
    val OnCardFaint = Color(0xFFFFFFFF).copy(alpha = 0.4f)

    // ── Text (dark on light) ──
    val TextPrimary = Color(0xFF1F1F1F)
    val TextSecondary = Color(0xFF5F6368)
    val TextTertiary = Color(0xFF9AA0A6)
    val TextCyan = Accent
}

/**
 * Chamfered rectangle — cuts top-right and bottom-left at 45 degrees.
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

// ── Chamfered path helper (reused by all draw functions) ──

private fun chamferedPath(w: Float, h: Float, cut: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(w - cut, 0f)
    lineTo(w, cut)
    lineTo(w, h)
    lineTo(cut, h)
    lineTo(0f, h - cut)
    close()
}

/**
 * Full PGR card treatment — layered borders, gradient fill, highlights, tech details.
 *
 * Layers (bottom to top):
 * 1. Gradient fill (lighter top → darker bottom)
 * 2. Outer border (bright, ~30% opacity)
 * 3. Inner border (dim, inset 2px)
 * 4. Top-edge highlight (white line along top edge, simulates light catch)
 * 5. Corner vertex accents (small bright dots at chamfer angle points)
 * 6. Tech line (partial line along bottom edge, ~60% width)
 * 7. Corner brackets (L-shaped targeting markers)
 */
fun DrawScope.drawPgrCard(
    fillLight: Color,
    fillDark: Color,
    borderColor: Color = Color.White,
    cutSize: Float,
    borderAlpha: Float = 0.3f
) {
    val w = size.width
    val h = size.height

    // 1. Gradient fill
    val fillPath = chamferedPath(w, h, cutSize)
    drawPath(
        fillPath,
        Brush.verticalGradient(
            colors = listOf(fillLight, fillDark),
            startY = 0f,
            endY = h
        )
    )

    // 2. Outer border
    drawPath(
        chamferedPath(w, h, cutSize),
        borderColor.copy(alpha = borderAlpha),
        style = Stroke(width = 1.2f)
    )

    // 3. Inner border (inset 2px)
    val inset = 2.5f
    val innerCut = (cutSize - inset).coerceAtLeast(0f)
    drawPath(
        chamferedPath(w - inset * 2, h - inset * 2, innerCut),
        borderColor.copy(alpha = borderAlpha * 0.4f),
        style = Stroke(width = 0.6f)
    )
    // Offset the inner path drawing — need to translate
    // (drawPath doesn't support offset, so we build the path with offset)
    val innerPath = Path().apply {
        moveTo(inset, inset)
        lineTo(w - inset - innerCut, inset)
        lineTo(w - inset, inset + innerCut)
        lineTo(w - inset, h - inset)
        lineTo(inset + innerCut, h - inset)
        lineTo(inset, h - inset - innerCut)
        close()
    }
    drawPath(
        innerPath,
        borderColor.copy(alpha = borderAlpha * 0.4f),
        style = Stroke(width = 0.5f)
    )

    // 4. Top-edge highlight (white line, simulates overhead light catch)
    drawLine(
        borderColor.copy(alpha = 0.25f),
        start = Offset(1f, 0.5f),
        end = Offset(w - cutSize - 1f, 0.5f),
        strokeWidth = 1f
    )

    // 5. Corner vertex accents (bright dots at the two chamfer angle points)
    val dotRadius = 2f
    // Top-right chamfer vertex
    drawCircle(
        borderColor.copy(alpha = 0.5f),
        radius = dotRadius,
        center = Offset(w - cutSize, 0f)
    )
    drawCircle(
        borderColor.copy(alpha = 0.5f),
        radius = dotRadius,
        center = Offset(w, cutSize)
    )
    // Bottom-left chamfer vertex
    drawCircle(
        borderColor.copy(alpha = 0.5f),
        radius = dotRadius,
        center = Offset(cutSize, h)
    )
    drawCircle(
        borderColor.copy(alpha = 0.5f),
        radius = dotRadius,
        center = Offset(0f, h - cutSize)
    )

    // 6. Tech line (partial bottom edge, ~60% width, offset from left)
    val techStart = w * 0.25f
    val techEnd = w * 0.85f
    drawLine(
        borderColor.copy(alpha = 0.15f),
        start = Offset(techStart, h - 1f),
        end = Offset(techEnd, h - 1f),
        strokeWidth = 0.5f
    )
    // Small perpendicular tick at the end
    drawLine(
        borderColor.copy(alpha = 0.15f),
        start = Offset(techEnd, h - 1f),
        end = Offset(techEnd, h - 4f),
        strokeWidth = 0.5f
    )

    // 7. Corner brackets
    val arm = 8f
    val bw = 0.7f
    val bc = borderColor.copy(alpha = 0.2f)
    // Top-left bracket
    drawLine(bc, Offset(3f, 3f), Offset(3f + arm, 3f), bw)
    drawLine(bc, Offset(3f, 3f), Offset(3f, 3f + arm), bw)
    // Bottom-right bracket
    drawLine(bc, Offset(w - 3f, h - 3f), Offset(w - 3f - arm, h - 3f), bw)
    drawLine(bc, Offset(w - 3f, h - 3f), Offset(w - 3f, h - 3f - arm), bw)
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
    drawLine(color, Offset(inset, inset), Offset(inset + armLength, inset), strokeWidth)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + armLength), strokeWidth)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset - armLength, inset), strokeWidth)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + armLength), strokeWidth)
    drawLine(color, Offset(inset, h - inset), Offset(inset + armLength, h - inset), strokeWidth)
    drawLine(color, Offset(inset, h - inset), Offset(inset, h - inset - armLength), strokeWidth)
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset - armLength, h - inset), strokeWidth)
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset, h - inset - armLength), strokeWidth)
}

/**
 * Draw chamfered border outline.
 */
fun DrawScope.drawChamferedBorder(
    color: Color,
    cutSize: Float,
    strokeWidth: Float = 1.5f
) {
    drawPath(chamferedPath(size.width, size.height, cutSize), color, style = Stroke(width = strokeWidth))
}

/**
 * Slow-scrolling scan line across the component.
 */
@Composable
fun Modifier.scanLine(
    color: Color = Pgr.Accent.copy(alpha = 0.08f),
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
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
    }
}
