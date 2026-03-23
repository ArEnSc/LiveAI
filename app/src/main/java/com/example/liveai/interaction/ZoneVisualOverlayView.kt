package com.example.liveai.interaction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Read-only overlay that draws all interaction zone rectangles over the
 * model preview. Shown when "Show Regions" is toggled on in the
 * interaction tab. Zones are transformed from model space to screen
 * space using the current model scale and offset.
 */
@SuppressLint("ViewConstructor")
class ZoneVisualOverlayView(
    context: Context,
    private var zones: List<InteractionZone>,
    private var modelScale: Float = 1f,
    private var modelOffsetX: Float = 0f,
    private var modelOffsetY: Float = 0f
) : View(context) {

    private val dp = context.resources.displayMetrics.density

    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 4f * dp), 0f)
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 12f * dp
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f * dp, 0f, 0f, Color.BLACK)
    }

    fun updateZones(newZones: List<InteractionZone>) {
        zones = newZones
        invalidate()
    }

    fun updateTransform(scale: Float, offsetX: Float, offsetY: Float) {
        modelScale = scale
        modelOffsetX = offsetX
        modelOffsetY = offsetY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (zone in zones) {
            val screenRect = ZoneTransform.modelToScreen(
                zone.rect, modelScale, modelOffsetX, modelOffsetY
            )
            val pixelRect = RectF(
                screenRect.left * w,
                screenRect.top * h,
                screenRect.right * w,
                screenRect.bottom * h
            )

            val fillAlpha = 0x44
            val fillColor = (fillAlpha shl 24) or (zone.color and 0x00FFFFFF)
            val borderColor = zone.color or (0xFF shl 24).toInt()

            fillPaint.color = fillColor
            borderPaint.color = borderColor

            canvas.drawRect(pixelRect, fillPaint)
            canvas.drawRect(pixelRect, borderPaint)

            // Zone name label
            canvas.drawText(
                zone.name,
                pixelRect.centerX(),
                pixelRect.centerY() + labelPaint.textSize / 3f,
                labelPaint
            )
        }
    }
}
