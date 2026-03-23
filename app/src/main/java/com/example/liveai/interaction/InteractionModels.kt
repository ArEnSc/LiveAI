package com.example.liveai.interaction

/** Intensity tier for LLM trigger messages. */
enum class Intensity(val word: String) {
    GENTLE("gently"),
    NORMAL(""),
    VIGOROUS("vigorously")
}

/** Per-pointer interaction state tracked during a drag. */
data class PointerInteraction(
    val zone: InteractionZone,
    val startX: Float,
    val startY: Float,
    var lastX: Float,
    var lastY: Float,
    var currentValues: Map<String, Float> = emptyMap()
)

/**
 * Callback interface for the interaction system to apply parameters to a model.
 * Decouples the interaction layer from LAppModel / LAppLive2DManager.
 */
interface InteractionTarget {
    /** Set interaction parameter values (applied before physics each frame). */
    fun setInteractionParams(params: Map<String, Float>)

    /** Clear specific interaction parameters by ID (restores normal behavior). */
    fun clearInteractionParams(paramIds: Set<String>)

    fun getScreenWidth(): Int
    fun getScreenHeight(): Int

    /** Current model scale factor (1.0 = default). */
    fun getModelScale(): Float
    /** Current model X offset in GL coordinates (screen spans [-1, 1]). */
    fun getModelOffsetX(): Float
    /** Current model Y offset in GL coordinates (screen spans [-1, 1]). */
    fun getModelOffsetY(): Float
}
