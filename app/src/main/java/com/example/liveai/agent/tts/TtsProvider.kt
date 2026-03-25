package com.example.liveai.agent.tts

/**
 * Interface for text-to-speech output. Implementations handle
 * audio playback via local engine, cloud API, or Android built-in.
 */
interface TtsProvider {

    /**
     * Speak the given text. Suspends until speech is complete or stopped.
     *
     * @param text Text to speak aloud
     */
    suspend fun speak(text: String)

    /**
     * Stop any in-progress speech immediately.
     */
    fun stop()

    /**
     * Whether the provider is currently speaking.
     */
    val isSpeaking: Boolean
}
