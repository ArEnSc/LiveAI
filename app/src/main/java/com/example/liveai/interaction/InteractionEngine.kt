package com.example.liveai.interaction

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure math for converting touch drag deltas into Live2D parameter angles.
 * No Android dependencies — all functions are stateless.
 */
object InteractionEngine {

    /** Default configs tuned for Android touch (larger finger movements than mouse). */
    val HEAD_CONFIG = InteractionConfig(
        sensitivity = 0.09f,
        angleClampXY = 30f,
        angleClampZ = 0f,
        spring = SpringConfig(
            durationMs = 1000L,
            decay = 8f,
            frequency = 2.5f,
            sinMultiplier = 12f
        )
    )

    val BODY_CONFIG = InteractionConfig(
        sensitivity = 0.07f,
        angleClampXY = 30f,
        angleClampZ = 15f,
        spring = SpringConfig(
            durationMs = 1200L,
            decay = 10f,
            frequency = 3f,
            sinMultiplier = 10f
        )
    )

    /** Intensity thresholds scaled for touch (larger than desktop mouse values). */
    private const val GENTLE_THRESHOLD = 15f
    private const val VIGOROUS_THRESHOLD = 40f

    fun clamp(value: Float, min: Float, max: Float): Float {
        return max(min, min(max, value))
    }

    /**
     * Convert drag pixel delta into head rotation angles.
     * deltaY is inverted: dragging down = negative angleY (look down / close eyes).
     */
    fun computeHeadAngles(deltaX: Float, deltaY: Float, config: InteractionConfig = HEAD_CONFIG): InteractionAngles {
        return InteractionAngles(
            angleX = clamp(deltaX * config.sensitivity, -config.angleClampXY, config.angleClampXY),
            angleY = clamp(-deltaY * config.sensitivity, -config.angleClampXY, config.angleClampXY)
        )
    }

    /**
     * Convert drag pixel delta into body rotation angles.
     * Z component is a twist/tilt derived from the diagonal of the drag.
     */
    fun computeBodyAngles(deltaX: Float, deltaY: Float, config: InteractionConfig = BODY_CONFIG): InteractionAngles {
        return InteractionAngles(
            angleX = clamp(deltaX * config.sensitivity, -config.angleClampXY, config.angleClampXY),
            angleY = clamp(-deltaY * config.sensitivity, -config.angleClampXY, config.angleClampXY),
            angleZ = clamp(
                (deltaX - deltaY) * config.sensitivity * 0.3f,
                -config.angleClampZ,
                config.angleClampZ
            )
        )
    }

    /**
     * Compute the magnitude of the final drag angles.
     * Used for intensity classification.
     */
    fun angleMagnitude(angles: InteractionAngles): Float {
        return sqrt(
            angles.angleX * angles.angleX +
            angles.angleY * angles.angleY +
            angles.angleZ * angles.angleZ
        )
    }

    /**
     * Classify drag intensity based on final angle magnitude.
     */
    fun classifyIntensity(magnitude: Float): Intensity {
        return when {
            magnitude < GENTLE_THRESHOLD -> Intensity.GENTLE
            magnitude < VIGOROUS_THRESHOLD -> Intensity.NORMAL
            else -> Intensity.VIGOROUS
        }
    }

    /**
     * Compute angles for a given body part from drag delta.
     */
    fun computeAngles(bodyPart: BodyPart, deltaX: Float, deltaY: Float): InteractionAngles {
        return when (bodyPart) {
            BodyPart.HEAD -> computeHeadAngles(deltaX, deltaY)
            BodyPart.BODY -> computeBodyAngles(deltaX, deltaY)
        }
    }

    /**
     * Get the config for a body part.
     */
    fun configFor(bodyPart: BodyPart): InteractionConfig {
        return when (bodyPart) {
            BodyPart.HEAD -> HEAD_CONFIG
            BodyPart.BODY -> BODY_CONFIG
        }
    }
}
