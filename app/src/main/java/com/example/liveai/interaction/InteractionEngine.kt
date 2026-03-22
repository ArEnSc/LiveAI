package com.example.liveai.interaction

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure math for converting touch drag deltas into Live2D parameter values.
 * No Android dependencies — all functions are stateless.
 */
object InteractionEngine {

    /** Default sensitivity lives on InteractionZone now. */

    /** Intensity thresholds (based on parameter-value magnitude). */
    private const val GENTLE_THRESHOLD = 5f
    private const val VIGOROUS_THRESHOLD = 15f

    fun clamp(value: Float, min: Float, max: Float): Float =
        max(min, min(max, value))

    /**
     * Compute parameter values for all bindings in a zone given a drag delta.
     *
     * For each binding:
     *   1. Pick the drag axis (deltaX or deltaY)
     *   2. Normalize to -1..1 via BASE_SENSITIVITY
     *   3. Scale by strength and maxValue
     *   4. Clamp to ±maxValue
     *
     * Returns paramId → value map.
     */
    fun computeBindingValues(
        bindings: List<ParameterBinding>,
        deltaX: Float,
        deltaY: Float,
        sensitivity: Float = InteractionZone.DEFAULT_SENSITIVITY
    ): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        for (binding in bindings) {
            val drag = when (binding.axis) {
                DragAxis.HORIZONTAL -> deltaX
                DragAxis.VERTICAL -> deltaY
            }
            val normalized = clamp(drag * sensitivity, -1f, 1f)
            val value = clamp(
                normalized * binding.strength * binding.maxValue,
                -binding.maxValue,
                binding.maxValue
            )
            result[binding.paramId] = value
        }
        return result
    }

    /**
     * Compute the overall magnitude of a parameter value map.
     * Used for intensity classification.
     */
    fun magnitude(values: Map<String, Float>): Float {
        var sumSq = 0f
        for (v in values.values) {
            sumSq += v * v
        }
        return sqrt(sumSq)
    }

    /**
     * Classify drag intensity based on parameter-value magnitude.
     */
    fun classifyIntensity(magnitude: Float): Intensity {
        return when {
            magnitude < GENTLE_THRESHOLD -> Intensity.GENTLE
            magnitude < VIGOROUS_THRESHOLD -> Intensity.NORMAL
            else -> Intensity.VIGOROUS
        }
    }
}
