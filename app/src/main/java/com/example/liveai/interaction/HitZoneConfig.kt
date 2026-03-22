package com.example.liveai.interaction

import android.content.Context
import android.graphics.RectF

/**
 * Persists hit zone rectangles for head and body interactions.
 *
 * Zones are stored as **normalized ratios** (0..1) relative to the screen,
 * so they adapt when the wallpaper runs at a different resolution than the
 * setup activity.
 */
object HitZoneConfig {

    private const val PREFS_NAME = "interaction_hit_zones"

    // Keys per body part
    private const val KEY_HEAD_LEFT = "head_left"
    private const val KEY_HEAD_TOP = "head_top"
    private const val KEY_HEAD_RIGHT = "head_right"
    private const val KEY_HEAD_BOTTOM = "head_bottom"
    private const val KEY_BODY_LEFT = "body_left"
    private const val KEY_BODY_TOP = "body_top"
    private const val KEY_BODY_RIGHT = "body_right"
    private const val KEY_BODY_BOTTOM = "body_bottom"

    // Defaults: centered, upper region for head, middle for body
    private val DEFAULT_HEAD = RectF(0.30f, 0.10f, 0.70f, 0.35f)
    private val DEFAULT_BODY = RectF(0.25f, 0.35f, 0.75f, 0.65f)

    fun loadHeadZone(context: Context): RectF {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return RectF(
            prefs.getFloat(KEY_HEAD_LEFT, DEFAULT_HEAD.left),
            prefs.getFloat(KEY_HEAD_TOP, DEFAULT_HEAD.top),
            prefs.getFloat(KEY_HEAD_RIGHT, DEFAULT_HEAD.right),
            prefs.getFloat(KEY_HEAD_BOTTOM, DEFAULT_HEAD.bottom)
        )
    }

    fun loadBodyZone(context: Context): RectF {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return RectF(
            prefs.getFloat(KEY_BODY_LEFT, DEFAULT_BODY.left),
            prefs.getFloat(KEY_BODY_TOP, DEFAULT_BODY.top),
            prefs.getFloat(KEY_BODY_RIGHT, DEFAULT_BODY.right),
            prefs.getFloat(KEY_BODY_BOTTOM, DEFAULT_BODY.bottom)
        )
    }

    fun save(context: Context, headZone: RectF, bodyZone: RectF) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_HEAD_LEFT, headZone.left)
            .putFloat(KEY_HEAD_TOP, headZone.top)
            .putFloat(KEY_HEAD_RIGHT, headZone.right)
            .putFloat(KEY_HEAD_BOTTOM, headZone.bottom)
            .putFloat(KEY_BODY_LEFT, bodyZone.left)
            .putFloat(KEY_BODY_TOP, bodyZone.top)
            .putFloat(KEY_BODY_RIGHT, bodyZone.right)
            .putFloat(KEY_BODY_BOTTOM, bodyZone.bottom)
            .apply()
    }

    /**
     * Convert a normalized zone (0..1) to pixel coordinates for a given screen size.
     */
    fun toPixels(zone: RectF, screenWidth: Float, screenHeight: Float): RectF {
        return RectF(
            zone.left * screenWidth,
            zone.top * screenHeight,
            zone.right * screenWidth,
            zone.bottom * screenHeight
        )
    }

    /**
     * Convert a pixel-space zone back to normalized ratios.
     */
    fun toNormalized(zone: RectF, screenWidth: Float, screenHeight: Float): RectF {
        return RectF(
            zone.left / screenWidth,
            zone.top / screenHeight,
            zone.right / screenWidth,
            zone.bottom / screenHeight
        )
    }
}
