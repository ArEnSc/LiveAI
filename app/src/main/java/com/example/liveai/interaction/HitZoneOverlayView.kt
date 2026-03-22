package com.example.liveai.interaction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Transparent overlay that draws draggable/resizable hit zone rectangles
 * over the model preview. Shown only while the user is editing hit zones.
 *
 * Each zone can be moved by dragging its interior, or resized by dragging
 * a corner handle.
 */
@SuppressLint("ViewConstructor")
class HitZoneOverlayView(
    context: Context,
    private var headZoneNorm: RectF,
    private var bodyZoneNorm: RectF,
    private val onZonesChanged: (head: RectF, body: RectF) -> Unit
) : View(context) {

    companion object {
        private const val HANDLE_RADIUS_DP = 10f
        private const val MIN_SIZE_DP = 44f
        private const val HEAD_COLOR = 0x664488FF.toInt()
        private const val BODY_COLOR = 0x6644FF88.toInt()
        private const val HEAD_BORDER_COLOR = 0xFF4488FF.toInt()
        private const val BODY_BORDER_COLOR = 0xFF44FF88.toInt()
    }

    private val dp = context.resources.displayMetrics.density
    private val handleRadius = HANDLE_RADIUS_DP * dp
    private val minSize = MIN_SIZE_DP * dp

    private val headFillPaint = Paint().apply { color = HEAD_COLOR; style = Paint.Style.FILL }
    private val bodyFillPaint = Paint().apply { color = BODY_COLOR; style = Paint.Style.FILL }
    private val headBorderPaint = Paint().apply {
        color = HEAD_BORDER_COLOR; style = Paint.Style.STROKE; strokeWidth = 2f * dp
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 4f * dp), 0f)
    }
    private val bodyBorderPaint = Paint().apply {
        color = BODY_BORDER_COLOR; style = Paint.Style.STROKE; strokeWidth = 2f * dp
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 4f * dp), 0f)
    }
    private val handlePaint = Paint().apply { style = Paint.Style.FILL }
    private val labelPaint = Paint().apply {
        color = Color.WHITE; textSize = 14f * dp; textAlign = Paint.Align.CENTER
        isFakeBoldText = true; setShadowLayer(3f * dp, 0f, 0f, Color.BLACK)
    }

    // Pixel-space rects (recomputed on layout / drag)
    private var headRect = RectF()
    private var bodyRect = RectF()

    // Drag state
    private enum class DragTarget { NONE, HEAD_MOVE, HEAD_CORNER, BODY_MOVE, BODY_CORNER }
    private var dragTarget = DragTarget.NONE
    private var dragCornerIndex = -1 // 0=TL, 1=TR, 2=BR, 3=BL
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragRectSnapshot = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        headRect = HitZoneConfig.toPixels(headZoneNorm, w.toFloat(), h.toFloat())
        bodyRect = HitZoneConfig.toPixels(bodyZoneNorm, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawZone(canvas, headRect, headFillPaint, headBorderPaint, HEAD_BORDER_COLOR, "HEAD")
        drawZone(canvas, bodyRect, bodyFillPaint, bodyBorderPaint, BODY_BORDER_COLOR, "BODY")
    }

    private fun drawZone(canvas: Canvas, rect: RectF, fill: Paint, border: Paint, handleColor: Int, label: String) {
        canvas.drawRect(rect, fill)
        canvas.drawRect(rect, border)

        // Corner handles
        handlePaint.color = handleColor
        val corners = cornersOf(rect)
        for (corner in corners) {
            canvas.drawCircle(corner.first, corner.second, handleRadius, handlePaint)
        }

        // Label
        canvas.drawText(label, rect.centerX(), rect.centerY() + labelPaint.textSize / 3f, labelPaint)
    }

    private fun cornersOf(r: RectF): List<Pair<Float, Float>> = listOf(
        r.left to r.top,
        r.right to r.top,
        r.right to r.bottom,
        r.left to r.bottom
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check corner handles first (head priority over body)
                val headCorner = hitCorner(headRect, x, y)
                if (headCorner >= 0) {
                    startDrag(DragTarget.HEAD_CORNER, headRect, x, y)
                    dragCornerIndex = headCorner
                    return true
                }
                val bodyCorner = hitCorner(bodyRect, x, y)
                if (bodyCorner >= 0) {
                    startDrag(DragTarget.BODY_CORNER, bodyRect, x, y)
                    dragCornerIndex = bodyCorner
                    return true
                }
                // Check interior (move)
                if (headRect.contains(x, y)) {
                    startDrag(DragTarget.HEAD_MOVE, headRect, x, y)
                    return true
                }
                if (bodyRect.contains(x, y)) {
                    startDrag(DragTarget.BODY_MOVE, bodyRect, x, y)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragTarget == DragTarget.NONE) return false

                val dx = x - dragStartX
                val dy = y - dragStartY
                val target = if (dragTarget == DragTarget.HEAD_MOVE || dragTarget == DragTarget.HEAD_CORNER) headRect else bodyRect

                when (dragTarget) {
                    DragTarget.HEAD_MOVE, DragTarget.BODY_MOVE -> {
                        val newLeft = (dragRectSnapshot.left + dx).coerceIn(0f, width.toFloat() - dragRectSnapshot.width())
                        val newTop = (dragRectSnapshot.top + dy).coerceIn(0f, height.toFloat() - dragRectSnapshot.height())
                        target.set(newLeft, newTop, newLeft + dragRectSnapshot.width(), newTop + dragRectSnapshot.height())
                    }
                    DragTarget.HEAD_CORNER, DragTarget.BODY_CORNER -> {
                        resizeByCorner(target, dragCornerIndex, dx, dy)
                    }
                    DragTarget.NONE -> {}
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTarget != DragTarget.NONE) {
                    dragTarget = DragTarget.NONE
                    syncNormalized()
                    onZonesChanged(headZoneNorm, bodyZoneNorm)
                    return true
                }
            }
        }
        return false
    }

    private fun startDrag(target: DragTarget, rect: RectF, x: Float, y: Float) {
        dragTarget = target
        dragStartX = x
        dragStartY = y
        dragRectSnapshot = RectF(rect)
    }

    private fun resizeByCorner(rect: RectF, corner: Int, dx: Float, dy: Float) {
        val snap = dragRectSnapshot
        when (corner) {
            0 -> { // top-left
                rect.left = (snap.left + dx).coerceIn(0f, snap.right - minSize)
                rect.top = (snap.top + dy).coerceIn(0f, snap.bottom - minSize)
            }
            1 -> { // top-right
                rect.right = (snap.right + dx).coerceIn(snap.left + minSize, width.toFloat())
                rect.top = (snap.top + dy).coerceIn(0f, snap.bottom - minSize)
            }
            2 -> { // bottom-right
                rect.right = (snap.right + dx).coerceIn(snap.left + minSize, width.toFloat())
                rect.bottom = (snap.bottom + dy).coerceIn(snap.top + minSize, height.toFloat())
            }
            3 -> { // bottom-left
                rect.left = (snap.left + dx).coerceIn(0f, snap.right - minSize)
                rect.bottom = (snap.bottom + dy).coerceIn(snap.top + minSize, height.toFloat())
            }
        }
    }

    private fun hitCorner(rect: RectF, x: Float, y: Float): Int {
        val touchRadius = handleRadius * 2f // generous touch target
        val corners = cornersOf(rect)
        for ((i, corner) in corners.withIndex()) {
            val dx = x - corner.first
            val dy = y - corner.second
            if (dx * dx + dy * dy <= touchRadius * touchRadius) return i
        }
        return -1
    }

    private fun syncNormalized() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        headZoneNorm = HitZoneConfig.toNormalized(headRect, w, h)
        bodyZoneNorm = HitZoneConfig.toNormalized(bodyRect, w, h)
    }

    /** Get current normalized zones. */
    fun getHeadZone(): RectF = RectF(headZoneNorm)
    fun getBodyZone(): RectF = RectF(bodyZoneNorm)
}
