package com.example.liveai.interaction

/** Which body part is being interacted with. */
enum class BodyPart {
    HEAD,
    BODY
}

/** Intensity tier for LLM trigger messages. */
enum class Intensity(val word: String) {
    GENTLE("gently"),
    NORMAL(""),
    VIGOROUS("vigorously")
}

/** Configuration for a single body-part interaction type. */
data class InteractionConfig(
    val sensitivity: Float,
    val angleClampXY: Float,
    val angleClampZ: Float,
    val spring: SpringConfig
)

/** Spring-back animation parameters (matches desktop JS curve). */
data class SpringConfig(
    val durationMs: Long,
    val decay: Float,
    val frequency: Float,
    val sinMultiplier: Float
)

/** Snapshot of angles at a point in time during interaction. */
data class InteractionAngles(
    val angleX: Float,
    val angleY: Float,
    val angleZ: Float = 0f
)

/** Per-pointer interaction state tracked during a drag. */
data class PointerInteraction(
    val bodyPart: BodyPart,
    val startX: Float,
    val startY: Float,
    var lastX: Float,
    var lastY: Float,
    var currentAngles: InteractionAngles = InteractionAngles(0f, 0f, 0f)
)
