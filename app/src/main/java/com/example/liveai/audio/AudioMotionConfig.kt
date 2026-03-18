package com.example.liveai.audio

/**
 * Configuration for audio-driven motion, mirroring the JS AudioMotionConfig.
 */
data class AudioMotionConfig(
    val enabled: Boolean = true,
    val intensity: Float = 1.0f,
    val speed: Float = 1.0f,
    val smoothing: Float = 0.85f,
    val volumeThreshold: Float = 0.02f,
    val bodyFollowRatio: Float = 0.3f,
    val headAngleCap: Float = 15f,
    val bodyAngleCap: Float = 5f,
    val paramEntries: List<ParamEntry> = defaultParamEntries()
)

data class ParamEntry(
    val paramId: String,
    val enabled: Boolean = true,
    val amplitude: Float = 1.0f,
    val isBody: Boolean = false,
    val harmonics: List<Harmonic> = listOf(Harmonic(1.0f, 1.0f))
)

data class Harmonic(
    val freqRatio: Float,
    val weight: Float
)

fun defaultParamEntries(): List<ParamEntry> = listOf(
    // Head nod (up/down)
    ParamEntry(
        paramId = "ParamAngleY",
        amplitude = 8f,
        harmonics = listOf(
            Harmonic(1.0f, 1.0f),
            Harmonic(2.3f, 0.4f),
            Harmonic(0.7f, 0.3f)
        )
    ),
    // Head sway (left/right)
    ParamEntry(
        paramId = "ParamAngleX",
        amplitude = 5f,
        harmonics = listOf(
            Harmonic(0.8f, 1.0f),
            Harmonic(1.7f, 0.3f)
        )
    ),
    // Head tilt
    ParamEntry(
        paramId = "ParamAngleZ",
        amplitude = 4f,
        harmonics = listOf(
            Harmonic(0.6f, 1.0f),
            Harmonic(1.9f, 0.2f)
        )
    ),
    // Body sway
    ParamEntry(
        paramId = "ParamBodyAngleX",
        amplitude = 3f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.5f, 1.0f),
            Harmonic(1.3f, 0.3f)
        )
    ),
    // Body tilt
    ParamEntry(
        paramId = "ParamBodyAngleZ",
        amplitude = 2f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.4f, 1.0f)
        )
    )
)
