package com.example.liveai.interaction

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Damped elastic spring-back animation.
 * Ports the desktop JS spring curve 1:1 — time-based, frame-rate independent.
 *
 * Usage:
 *   val spring = SpringAnimator(startAngles, config)
 *   // Each frame:
 *   val current = spring.update(elapsedMs)
 *   if (spring.isFinished) { ... }
 */
class SpringAnimator(
    private val startAngles: InteractionAngles,
    private val config: SpringConfig
) {
    private val startTimeMs = System.currentTimeMillis()

    var isFinished: Boolean = false
        private set

    /**
     * Get the current spring-interpolated angles.
     * Call once per frame. Returns angles converging toward zero.
     */
    fun update(): InteractionAngles {
        val elapsedMs = System.currentTimeMillis() - startTimeMs
        return update(elapsedMs)
    }

    /**
     * Testable overload: pass elapsed time explicitly.
     */
    fun update(elapsedMs: Long): InteractionAngles {
        val progress = min(elapsedMs.toFloat() / config.durationMs.toFloat(), 1f)

        if (progress >= 1f) {
            isFinished = true
            return InteractionAngles(0f, 0f, 0f)
        }

        val eased = elasticEaseOut(progress)
        val factor = 1f - eased

        return InteractionAngles(
            angleX = startAngles.angleX * factor,
            angleY = startAngles.angleY * factor,
            angleZ = startAngles.angleZ * factor
        )
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

    companion object {
        /** Convenience: create a spring for a body part using its default config. */
        fun forBodyPart(bodyPart: BodyPart, startAngles: InteractionAngles): SpringAnimator {
            return SpringAnimator(startAngles, InteractionEngine.configFor(bodyPart).spring)
        }
    }
}
