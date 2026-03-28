package com.example.liveai.chat

import android.content.Context

/**
 * Persisted physics tuning parameters for chat head spring/fling behavior.
 * Mirrors FilterSettings pattern — SharedPreferences with explicit defaults.
 */
object ChatHeadSettings {

    private const val PREFS_NAME = "chat_head_settings"

    private const val KEY_SPRING_STIFFNESS = "spring_stiffness"
    private const val KEY_SPRING_DAMPING = "spring_damping"
    private const val KEY_FLING_FRICTION = "fling_friction"
    private const val KEY_SNAP_ENABLED = "snap_enabled"

    // Defaults: floaty-but-responsive feel
    const val DEFAULT_STIFFNESS = 400f
    const val DEFAULT_DAMPING = 0.5f
    const val DEFAULT_FRICTION = 1.2f
    const val DEFAULT_SNAP_ENABLED = true

    // Ranges for sliders
    const val STIFFNESS_MIN = 50f
    const val STIFFNESS_MAX = 2000f
    const val DAMPING_MIN = 0.1f
    const val DAMPING_MAX = 1.0f
    const val FRICTION_MIN = 0.5f
    const val FRICTION_MAX = 5.0f

    data class Physics(
        val springStiffness: Float = DEFAULT_STIFFNESS,
        val springDamping: Float = DEFAULT_DAMPING,
        val flingFriction: Float = DEFAULT_FRICTION,
        val snapToEdge: Boolean = DEFAULT_SNAP_ENABLED
    )

    fun load(context: Context): Physics {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Physics(
            springStiffness = prefs.getFloat(KEY_SPRING_STIFFNESS, DEFAULT_STIFFNESS),
            springDamping = prefs.getFloat(KEY_SPRING_DAMPING, DEFAULT_DAMPING),
            flingFriction = prefs.getFloat(KEY_FLING_FRICTION, DEFAULT_FRICTION),
            snapToEdge = prefs.getBoolean(KEY_SNAP_ENABLED, DEFAULT_SNAP_ENABLED)
        )
    }

    fun save(context: Context, physics: Physics) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SPRING_STIFFNESS, physics.springStiffness)
            .putFloat(KEY_SPRING_DAMPING, physics.springDamping)
            .putFloat(KEY_FLING_FRICTION, physics.flingFriction)
            .putBoolean(KEY_SNAP_ENABLED, physics.snapToEdge)
            .apply()
    }

    fun reset(context: Context) {
        save(context, Physics())
    }
}
