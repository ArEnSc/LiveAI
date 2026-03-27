package com.example.liveai

import android.content.Context
import com.example.liveai.live2d.PostProcessFilter

object FilterSettings {
    const val PREFS_NAME = "filter_settings"
    const val KEY_SATURATION = "saturation_enabled"
    const val KEY_OUTLINE = "outline_enabled"
    const val KEY_SATURATION_AMOUNT = "saturation_amount"
    const val KEY_OUTLINE_THICKNESS = "outline_thickness"
    const val KEY_OUTLINE_COLOR_R = "outline_color_r"
    const val KEY_OUTLINE_COLOR_G = "outline_color_g"
    const val KEY_OUTLINE_COLOR_B = "outline_color_b"

    // Audio motion
    const val KEY_AUDIO_MOTION_ENABLED = "audio_motion_enabled"
    const val KEY_AUDIO_MOTION_INTENSITY = "audio_motion_intensity"
    const val KEY_AUDIO_MOTION_SPEED = "audio_motion_speed"


    fun loadInto(context: Context, postProcess: PostProcessFilter) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        postProcess.isSaturationEnabled = prefs.getBoolean(KEY_SATURATION, false)
        postProcess.isOutlineEnabled = prefs.getBoolean(KEY_OUTLINE, false)
        postProcess.saturationAmount = prefs.getFloat(KEY_SATURATION_AMOUNT, 1.5f)
        postProcess.outlineThickness = prefs.getFloat(KEY_OUTLINE_THICKNESS, 1.5f)
        postProcess.setOutlineColor(
            prefs.getFloat(KEY_OUTLINE_COLOR_R, 0.0f),
            prefs.getFloat(KEY_OUTLINE_COLOR_G, 0.0f),
            prefs.getFloat(KEY_OUTLINE_COLOR_B, 0.0f),
            1.0f
        )
    }
}
