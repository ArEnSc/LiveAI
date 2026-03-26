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
 * Design system inspired by PGR / augmented-ui / cyberpunk card kits.
 * Layered borders, gradient fills, chamfered corners, highlight edges.
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

    // ── Status card fills: light (top) → dark (bottom) gradient ──
    val BgRunning = Color(0xFF8B73FF)
    val BgRunningDark = Color(0xFF5A43D9)
    val BgQueued = Color(0xFF90A4AE)
    val BgQueuedDark = Color(0xFF607D8B)
    val BgSuspended = Color(0xFFF0B840)
    val BgSuspendedDark = Color(0xFFC68A00)
    val BgCompleted = Color(0xFF66BB6A)
    val BgCompletedDark = Color(0xFF2E7D32)
    val BgFailed = Color(0xFFEF5350)
    val BgFailedDark = Color(0xFFC62828)
    val BgCancelled = Color(0xFF8E8E8E)
    val BgCancelledDark = Color(0xFF616161)

    // ── Tool call fills ──
    val BgToolActive = Color(0xFF8B73FF)
    val BgToolActiveDark = Color(0xFF5A43D9)
    val BgToolDone = Color(0xFF66BB6A)
    val BgToolDoneDark = Color(0xFF2E7D32)
    val BgToolError = Color(0xFFEF5350)
    val BgToolErrorDark = Color(0xFFC62828)

    // ── Text on solid cards ──
    val OnCard = Color(0xFFFFFFFF)
    val OnCardDim = Color(0xFFFFFFFF).copy(alpha = 0.75f)
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
        return androidx.compose.ui.graphics.Outline.Generic(chamferedPath(size.width, size.height, cut))
    }
}

// ── Path helpers ──

private fun chamferedPath(w: Float, h: Float, cut: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(w - cut, 0f)       // top edge → top-right chamfer start
    lineTo(w, cut)             // top-right chamfer diagonal
    lineTo(w, h)               // right edge
    lineTo(cut, h)             // bottom edge → bottom-left chamfer start
    lineTo(0f, h - cut)        // bottom-left chamfer diagonal
    close()
}

private fun chamferedPathInset(w: Float, h: Float, cut: Float, inset: Float): Path {
    val ic = (cut - inset * 0.7f).coerceAtLeast(1f)
    return Path().apply {
        moveTo(inset, inset)
        lineTo(w - inset - ic, inset)
        lineTo(w - inset, inset + ic)
        lineTo(w - inset, h - inset)
        lineTo(inset + ic, h - inset)
        lineTo(inset, h - inset - ic)
        close()
    }
}

/**
 * Full PGR card treatment with layered borders.
 *
 * Layers:
 * 1. Gradient fill (light top → dark bottom)
 * 2. Outer border (following chamfered shape)
 * 3. Inner inlay border (inset, dimmer — creates depth)
 * 4. Top-edge highlight (thin bright line — light catch)
 * 5. Left-edge highlight (thin bright line — side light)
 * 6. Bottom-edge shadow (thin dark line — grounding)
 * 7. Chamfer vertex dots (bright accents at angle changes)
 * 8. Tech line (partial decorative line with tick mark)
 */
fun DrawScope.drawPgrCard(
    fillLight: Color,
    fillDark: Color,
    borderColor: Color = Color.White,
    cutSize: Float
) {
    val w = size.width
    val h = size.height

    // 1. Gradient fill
    drawPath(
        chamferedPath(w, h, cutSize),
        Brush.verticalGradient(listOf(fillLight, fillDark))
    )

    // 2. Outer border
    drawPath(
        chamferedPath(w, h, cutSize),
        borderColor.copy(alpha = 0.35f),
        style = Stroke(width = 1.5f)
    )

    // 3. Inner inlay border (inset 3px, dimmer)
    drawPath(
        chamferedPathInset(w, h, cutSize, 3f),
        borderColor.copy(alpha = 0.12f),
        style = Stroke(width = 0.7f)
    )

    // 4. Top-edge highlight
    drawLine(
        borderColor.copy(alpha = 0.4f),
        Offset(2f, 1f),
        Offset(w - cutSize - 2f, 1f),
        strokeWidth = 1f
    )

    // 5. Left-edge highlight (partial, top 60%)
    drawLine(
        borderColor.copy(alpha = 0.15f),
        Offset(1f, 2f),
        Offset(1f, h * 0.6f),
        strokeWidth = 0.7f
    )

    // 6. Bottom-edge shadow
    drawLine(
        Color.Black.copy(alpha = 0.15f),
        Offset(cutSize + 2f, h - 1f),
        Offset(w - 2f, h - 1f),
        strokeWidth = 1f
    )

    // 7. Chamfer vertex dots
    val dotR = 1.8f
    val dotColor = borderColor.copy(alpha = 0.6f)
    drawCircle(dotColor, dotR, Offset(w - cutSize, 0f))      // top-right start
    drawCircle(dotColor, dotR, Offset(w, cutSize))            // top-right end
    drawCircle(dotColor, dotR, Offset(cutSize, h))            // bottom-left start
    drawCircle(dotColor, dotR, Offset(0f, h - cutSize))       // bottom-left end

    // 8. Tech line (bottom edge, 55% width, with perpendicular tick)
    val tl = borderColor.copy(alpha = 0.1f)
    val techStart = w * 0.3f
    val techEnd = w * 0.85f
    drawLine(tl, Offset(techStart, h - 3f), Offset(techEnd, h - 3f), 0.5f)
    drawLine(tl, Offset(techEnd, h - 3f), Offset(techEnd, h - 7f), 0.5f)
    // Small dot at tech line start
    drawCircle(tl, 1f, Offset(techStart, h - 3f))
}

/**
 * Draw corner bracket decorations — L-shaped targeting markers.
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
 * Slow-scrolling scan line.
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
