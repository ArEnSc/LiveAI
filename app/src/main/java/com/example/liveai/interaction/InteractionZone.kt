package com.example.liveai.interaction

import android.graphics.RectF

/** Spring-back animation parameters (matches desktop JS curve). */
data class SpringConfig(
    val durationMs: Long,
    val decay: Float,
    val frequency: Float,
    val sinMultiplier: Float
)

/**
 * A user-configurable touch interaction zone on the model.
 * Each zone maps a screen region to a set of Live2D parameter bindings.
 */
data class InteractionZone(
    val id: String,
    val name: String,
    val color: Int,
    val rect: RectF,
    val bindings: List<ParameterBinding>,
    val spring: SpringConfig,
    val sensitivity: Float = DEFAULT_SENSITIVITY,
    /** Parameters held at fixed values while the zone is actively touched. */
    val holdParams: Map<String, Float> = emptyMap(),
    /** Core zones (Head, Body) cannot be deleted by the user. */
    val core: Boolean = false
) {
    companion object {
        const val DEFAULT_SENSITIVITY = 0.01f
    }
}

/**
 * Maps a drag axis to a Live2D parameter with a strength multiplier.
 */
data class ParameterBinding(
    val paramId: String,
    val displayName: String,
    val axis: DragAxis,
    val strength: Float,
    val maxValue: Float
)

enum class DragAxis {
    HORIZONTAL,
    VERTICAL
}
