package com.example.liveai.audio

/**
 * Configuration for audio-driven motion.
 *
 * Uses sinusoidal oscillators at incommensurate frequency ratios,
 * modulated by a smoothed audio volume envelope, to produce natural-looking
 * head nods, sway, tilt, and subtle body movement.
 */
data class AudioMotionConfig(
    val enabled: Boolean = true,
    val intensity: Float = 0.30f,
    val speed: Float = 2.0f,
    val smoothing: Float = 0.85f,
    val volumeThreshold: Float = 0.02f,
    val bodyFollowRatio: Float = 0.5f,
    val headAngleCap: Float = 15f,
    val bodyAngleCap: Float = 8f,
    val paramEntries: List<ParamEntry> = defaultSpeakingEntries()
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

/** Speaking motion: faster (2.0 Hz), lower amplitude, quick subtle movements */
fun defaultSpeakingEntries(): List<ParamEntry> = listOf(
    // Head Nod (up/down)
    ParamEntry(
        paramId = "ParamAngleY",
        amplitude = 6.0f,
        harmonics = listOf(
            Harmonic(1.0f, 1.0f),
            Harmonic(2.3f, 0.5f)
        )
    ),
    // Head Tilt (left/right)
    ParamEntry(
        paramId = "ParamAngleX",
        amplitude = 4.0f,
        harmonics = listOf(
            Harmonic(0.7f, 1.0f),
            Harmonic(1.6f, 0.5f)
        )
    ),
    // Head Roll
    ParamEntry(
        paramId = "ParamAngleZ",
        amplitude = 3.0f,
        harmonics = listOf(
            Harmonic(0.5f, 1.0f),
            Harmonic(1.3f, 0.5f)
        )
    ),
    // Body Sway (standard)
    ParamEntry(
        paramId = "ParamBodyAngleX",
        amplitude = 3.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.35f, 1.0f)
        )
    ),
    // Body Nod (standard)
    ParamEntry(
        paramId = "ParamBodyAngleY",
        amplitude = 2.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.5f, 1.0f)
        )
    ),
    // Body Z (standard)
    ParamEntry(
        paramId = "ParamBodyAngleZ",
        amplitude = 1.5f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.3f, 1.0f)
        )
    ),
    // Extended body X
    ParamEntry(
        paramId = "ParamA_BodyX",
        amplitude = 2.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.35f, 1.0f)
        )
    ),
    // Extended body Y
    ParamEntry(
        paramId = "ParamA_BodyY",
        amplitude = 1.5f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.5f, 1.0f)
        )
    ),
    // Extended body Z
    ParamEntry(
        paramId = "ParamA_BodyZ",
        amplitude = 1.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.4f, 1.0f)
        )
    ),
    // Secondary body params
    ParamEntry(
        paramId = "ParamBodyX2",
        amplitude = 1.5f,
        isBody = true,
        harmonics = listOf(Harmonic(0.45f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamBodyY2",
        amplitude = 1.0f,
        isBody = true,
        harmonics = listOf(Harmonic(0.55f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamBodyZ2",
        amplitude = 0.8f,
        isBody = true,
        harmonics = listOf(Harmonic(0.38f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamA_BodyX2",
        amplitude = 1.0f,
        isBody = true,
        harmonics = listOf(Harmonic(0.42f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamA_BodyY2",
        amplitude = 0.8f,
        isBody = true,
        harmonics = listOf(Harmonic(0.48f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamA_BodyZ2",
        amplitude = 0.6f,
        isBody = true,
        harmonics = listOf(Harmonic(0.36f, 1.0f))
    )
)

/** Listening motion: slower (1.0 Hz), higher head amplitude, gentle swaying */
fun defaultListeningEntries(): List<ParamEntry> = listOf(
    ParamEntry(
        paramId = "ParamAngleY",
        amplitude = 8.0f,
        harmonics = listOf(
            Harmonic(1.0f, 1.0f),
            Harmonic(1.8f, 0.5f)
        )
    ),
    ParamEntry(
        paramId = "ParamAngleX",
        amplitude = 3.0f,
        harmonics = listOf(
            Harmonic(0.6f, 1.0f),
            Harmonic(1.4f, 0.5f)
        )
    ),
    ParamEntry(
        paramId = "ParamAngleZ",
        amplitude = 2.0f,
        harmonics = listOf(
            Harmonic(0.45f, 1.0f),
            Harmonic(1.1f, 0.5f)
        )
    ),
    ParamEntry(
        paramId = "ParamBodyAngleX",
        amplitude = 2.5f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.3f, 1.0f)
        )
    ),
    ParamEntry(
        paramId = "ParamBodyAngleY",
        amplitude = 2.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.4f, 1.0f)
        )
    ),
    ParamEntry(
        paramId = "ParamBodyAngleZ",
        amplitude = 1.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.25f, 1.0f)
        )
    ),
    ParamEntry(
        paramId = "ParamA_BodyX",
        amplitude = 1.5f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.3f, 1.0f)
        )
    ),
    ParamEntry(
        paramId = "ParamA_BodyY",
        amplitude = 1.0f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.4f, 1.0f)
        )
    ),
    ParamEntry(
        paramId = "ParamA_BodyZ",
        amplitude = 0.8f,
        isBody = true,
        harmonics = listOf(
            Harmonic(0.35f, 1.0f)
        )
    ),
    ParamEntry(
        paramId = "ParamBodyX2",
        amplitude = 1.0f,
        isBody = true,
        harmonics = listOf(Harmonic(0.4f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamBodyY2",
        amplitude = 0.8f,
        isBody = true,
        harmonics = listOf(Harmonic(0.5f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamBodyZ2",
        amplitude = 0.6f,
        isBody = true,
        harmonics = listOf(Harmonic(0.32f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamA_BodyX2",
        amplitude = 0.8f,
        isBody = true,
        harmonics = listOf(Harmonic(0.38f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamA_BodyY2",
        amplitude = 0.6f,
        isBody = true,
        harmonics = listOf(Harmonic(0.42f, 1.0f))
    ),
    ParamEntry(
        paramId = "ParamA_BodyZ2",
        amplitude = 0.5f,
        isBody = true,
        harmonics = listOf(Harmonic(0.3f, 1.0f))
    )
)

val DEFAULT_SPEAKING_CONFIG = AudioMotionConfig(
    intensity = 0.30f,
    speed = 2.0f,
    bodyFollowRatio = 0.5f,
    volumeThreshold = 0.02f,
    smoothing = 0.85f,
    headAngleCap = 15f,
    bodyAngleCap = 8f,
    paramEntries = defaultSpeakingEntries()
)

val DEFAULT_LISTENING_CONFIG = AudioMotionConfig(
    intensity = 0.25f,
    speed = 1.0f,
    bodyFollowRatio = 0.4f,
    volumeThreshold = 0.008f,
    smoothing = 0.88f,
    headAngleCap = 20f,
    bodyAngleCap = 6f,
    paramEntries = defaultListeningEntries()
)
