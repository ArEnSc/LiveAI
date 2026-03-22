package com.example.liveai.interaction

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Damped elastic spring-back animation for arbitrary parameter maps.
 * Ports the desktop JS spring curve 1:1 — time-based, frame-rate independent.
 *
 * Usage:
 *   val spring = SpringAnimator(startValues, config)
 *   // Each frame:
 *   val current = spring.update()
 *   if (spring.isFinished) { ... }
 */
class SpringAnimator(
    private val startValues: Map<String, Float>,
    private val config: SpringConfig
) {
    private val startTimeMs = System.currentTimeMillis()

    var isFinished: Boolean = false
        private set

    /**
     * Get the current spring-interpolated parameter values.
     * Call once per frame. Returns values converging toward zero.
     */
    fun update(): Map<String, Float> {
        val elapsedMs = System.currentTimeMillis() - startTimeMs
        return update(elapsedMs)
    }

    /**
     * Testable overload: pass elapsed time explicitly.
     */
    fun update(elapsedMs: Long): Map<String, Float> {
        val progress = min(elapsedMs.toFloat() / config.durationMs.toFloat(), 1f)

        if (progress >= 1f) {
            isFinished = true
            return startValues.mapValues { 0f }
        }

        val factor = 1f - elasticEaseOut(progress)
        return startValues.mapValues { (_, v) -> v * factor }
    }

    /**
     * Elastic ease-out: damped sinusoidal oscillation.
     * Matches the desktop implementation exactly.
     */
    private fun elasticEaseOut(progress: Float): Float {
        if (progress == 0f) return 0f
        if (progress == 1f) return 1f

        val c4 = (2.0 * PI / config.frequency).toFloat()

        return 2f.pow(-config.decay * progress) *
            sin(((progress * config.sinMultiplier - 0.75f) * c4)) + 1f
    }
}
