package com.example.liveai.interaction

import android.graphics.RectF

/**
 * Converts zone rectangles between model-relative normalized coordinates
 * and screen-relative normalized coordinates, accounting for model
 * scale and offset.
 *
 * Zone rects are stored in **model space** — normalized [0,1] coordinates
 * where (0.5, 0.5) is the center of the model at its default position.
 * When the model is scaled or panned, these coordinates are transformed
 * to screen space so that zones follow the model.
 *
 * Coordinate systems:
 * - **Model space**: [0,1] normalized, (0.5, 0.5) = model center at default
 * - **Screen space**: [0,1] normalized, (0,0) = top-left of screen
 * - **GL offset**: model offset in GL coords where screen spans [-1, 1]
 */
object ZoneTransform {

    /**
     * Transform a zone rect from model space to screen space.
     *
     * @param modelRect zone rect in model-relative [0,1] coordinates
     * @param scale current model scale factor (1.0 = default)
     * @param offsetX model X offset in GL coordinates (screen spans [-1, 1])
     * @param offsetY model Y offset in GL coordinates (screen spans [-1, 1], positive = up)
     * @return rect in screen-relative [0,1] coordinates
     */
    fun modelToScreen(modelRect: RectF, scale: Float, offsetX: Float, offsetY: Float): RectF {
        return RectF(
            modelToScreenX(modelRect.left, scale, offsetX),
            modelToScreenY(modelRect.top, scale, offsetY),
            modelToScreenX(modelRect.right, scale, offsetX),
            modelToScreenY(modelRect.bottom, scale, offsetY)
        )
    }

    /**
     * Transform a zone rect from screen space back to model space.
     *
     * @param screenRect zone rect in screen-relative [0,1] coordinates
     * @param scale current model scale factor
     * @param offsetX model X offset in GL coordinates
     * @param offsetY model Y offset in GL coordinates
     * @return rect in model-relative [0,1] coordinates
     */
    fun screenToModel(screenRect: RectF, scale: Float, offsetX: Float, offsetY: Float): RectF {
        if (scale == 0f) return RectF(screenRect)
        return RectF(
            screenToModelX(screenRect.left, scale, offsetX),
            screenToModelY(screenRect.top, scale, offsetY),
            screenToModelX(screenRect.right, scale, offsetX),
            screenToModelY(screenRect.bottom, scale, offsetY)
        )
    }

    private fun modelToScreenX(mx: Float, scale: Float, offsetX: Float): Float {
        // Center-relative → scale → translate → back to [0,1]
        return (mx - 0.5f) * scale + offsetX / 2f + 0.5f
    }

    private fun modelToScreenY(my: Float, scale: Float, offsetY: Float): Float {
        // GL Y is inverted relative to screen Y, so subtract offsetY
        return (my - 0.5f) * scale - offsetY / 2f + 0.5f
    }

    private fun screenToModelX(sx: Float, scale: Float, offsetX: Float): Float {
        return (sx - 0.5f - offsetX / 2f) / scale + 0.5f
    }

    private fun screenToModelY(sy: Float, scale: Float, offsetY: Float): Float {
        return (sy - 0.5f + offsetY / 2f) / scale + 0.5f
    }
}
