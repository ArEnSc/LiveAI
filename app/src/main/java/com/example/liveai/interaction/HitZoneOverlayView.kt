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
 * Transparent overlay that draws a single draggable/resizable zone rectangle
 * over the model preview. Shown while the user is positioning a zone.
 *
 * Zone coordinates are stored in **model space** (normalized [0,1] relative
 * to the model at default position). The overlay transforms them to screen
 * space for display using the current model scale and offset, and
 * inverse-transforms back to model space when saving.
 */
@SuppressLint("ViewConstructor")
class HitZoneOverlayView(
    context: Context,
    private var zoneModelNorm: RectF,
    private val zoneColor: Int,
    private var modelScale: Float = 1f,
    private var modelOffsetX: Float = 0f,
    private var modelOffsetY: Float = 0f,
    private val onZoneChanged: (RectF) -> Unit
) : View(context) {

    companion object {
        private const val HANDLE_RADIUS_DP = 10f
        private const val MIN_SIZE_DP = 44f
    }

    private val dp = context.resources.displayMetrics.density
    private val handleRadius = HANDLE_RADIUS_DP * dp
    private val minSize = MIN_SIZE_DP * dp

    private val fillAlpha = 0x66
    private val fillColor = (fillAlpha shl 24) or (zoneColor and 0x00FFFFFF)
    private val borderColor = zoneColor or (0xFF shl 24).toInt()

    private val fillPaint = Paint().apply { color = fillColor; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply {
        color = borderColor; style = Paint.Style.STROKE; strokeWidth = 2f * dp
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 4f * dp), 0f)
    }
    private val handlePaint = Paint().apply { color = borderColor; style = Paint.Style.FILL }
    private val labelPaint = Paint().apply {
        color = Color.WHITE; textSize = 14f * dp; textAlign = Paint.Align.CENTER
        isFakeBoldText = true; setShadowLayer(3f * dp, 0f, 0f, Color.BLACK)
    }

    private var pixelRect = RectF()

    // Drag state
    private enum class DragMode { NONE, MOVE, CORNER }
    private var dragMode = DragMode.NONE
    private var dragCornerIndex = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragRectSnapshot = RectF()

    fun updateTransform(scale: Float, offsetX: Float, offsetY: Float) {
        modelScale = scale
        modelOffsetX = offsetX
        modelOffsetY = offsetY
        if (width > 0 && height > 0) {
            pixelRect = modelToPixels(zoneModelNorm)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pixelRect = modelToPixels(zoneModelNorm)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(pixelRect, fillPaint)
        canvas.drawRect(pixelRect, borderPaint)

        val corners = cornersOf(pixelRect)
        for (corner in corners) {
            canvas.drawCircle(corner.first, corner.second, handleRadius, handlePaint)
        }

        canvas.drawText(
            "Drag to move \u2022 Corners to resize",
            pixelRect.centerX(),
            pixelRect.centerY() + labelPaint.textSize / 3f,
            labelPaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val corner = hitCorner(pixelRect, x, y)
                if (corner >= 0) {
                    startDrag(DragMode.CORNER, x, y)
                    dragCornerIndex = corner
                    return true
                }
                if (pixelRect.contains(x, y)) {
                    startDrag(DragMode.MOVE, x, y)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragMode == DragMode.NONE) return false
                val dx = x - dragStartX
                val dy = y - dragStartY

                when (dragMode) {
                    DragMode.MOVE -> {
                        val newLeft = (dragRectSnapshot.left + dx)
                            .coerceIn(0f, width.toFloat() - dragRectSnapshot.width())
                        val newTop = (dragRectSnapshot.top + dy)
                            .coerceIn(0f, height.toFloat() - dragRectSnapshot.height())
                        pixelRect.set(
                            newLeft, newTop,
                            newLeft + dragRectSnapshot.width(),
                            newTop + dragRectSnapshot.height()
                        )
                    }
                    DragMode.CORNER -> resizeByCorner(dragCornerIndex, dx, dy)
                    DragMode.NONE -> {}
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode != DragMode.NONE) {
                    dragMode = DragMode.NONE
                    syncToModelSpace()
                    onZoneChanged(zoneModelNorm)
                    return true
                }
            }
        }
        return false
    }

    fun getZoneNorm(): RectF = RectF(zoneModelNorm)

    private fun startDrag(mode: DragMode, x: Float, y: Float) {
        dragMode = mode
        dragStartX = x
        dragStartY = y
        dragRectSnapshot = RectF(pixelRect)
    }

    private fun resizeByCorner(corner: Int, dx: Float, dy: Float) {
        val snap = dragRectSnapshot
        when (corner) {
            0 -> {
                pixelRect.left = (snap.left + dx).coerceIn(0f, snap.right - minSize)
                pixelRect.top = (snap.top + dy).coerceIn(0f, snap.bottom - minSize)
            }
            1 -> {
                pixelRect.right = (snap.right + dx).coerceIn(snap.left + minSize, width.toFloat())
                pixelRect.top = (snap.top + dy).coerceIn(0f, snap.bottom - minSize)
            }
            2 -> {
                pixelRect.right = (snap.right + dx).coerceIn(snap.left + minSize, width.toFloat())
                pixelRect.bottom = (snap.bottom + dy).coerceIn(snap.top + minSize, height.toFloat())
            }
            3 -> {
                pixelRect.left = (snap.left + dx).coerceIn(0f, snap.right - minSize)
                pixelRect.bottom = (snap.bottom + dy).coerceIn(snap.top + minSize, height.toFloat())
            }
        }
    }

    private fun hitCorner(rect: RectF, x: Float, y: Float): Int {
        val touchRadius = handleRadius * 2f
        for ((i, corner) in cornersOf(rect).withIndex()) {
            val cx = x - corner.first
            val cy = y - corner.second
            if (cx * cx + cy * cy <= touchRadius * touchRadius) return i
        }
        return -1
    }

    /** Convert pixel rect back to model-space normalized coordinates. */
    private fun syncToModelSpace() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Pixel → screen-space normalized
        val screenNorm = RectF(
            pixelRect.left / w, pixelRect.top / h,
            pixelRect.right / w, pixelRect.bottom / h
        )
        // Screen-space → model-space
        zoneModelNorm = ZoneTransform.screenToModel(
            screenNorm, modelScale, modelOffsetX, modelOffsetY
        )
    }

    /** Convert model-space normalized rect to pixel coordinates. */
    private fun modelToPixels(modelNorm: RectF): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        val screenNorm = ZoneTransform.modelToScreen(
            modelNorm, modelScale, modelOffsetX, modelOffsetY
        )
        return RectF(
            screenNorm.left * w, screenNorm.top * h,
            screenNorm.right * w, screenNorm.bottom * h
        )
    }

    private fun cornersOf(r: RectF): List<Pair<Float, Float>> = listOf(
        r.left to r.top, r.right to r.top,
        r.right to r.bottom, r.left to r.bottom
    )
}
