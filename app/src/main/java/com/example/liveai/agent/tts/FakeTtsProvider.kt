package com.example.liveai.agent.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Fake TTS provider that records spoken text without playing audio.
 * Use in tests to verify what was spoken and in what order.
 */
class FakeTtsProvider(
    private val speakDelayMs: Long = 0L
) : TtsProvider {

    private var _isSpeaking = false
    override val isSpeaking: Boolean get() = _isSpeaking

    /** All texts passed to speak(), in order. */
    val spokenTexts: MutableList<String> = mutableListOf()

    /** Whether stop() was called. */
    var stopCalled: Boolean = false
        private set

    override suspend fun speak(text: String) {
        spokenTexts.add(text)
        _isSpeaking = true
        try {
            if (speakDelayMs > 0) {
                delay(speakDelayMs)
            }
        } catch (e: CancellationException) {
            _isSpeaking = false
            throw e
        }
        _isSpeaking = false
    }

    override fun stop() {
        stopCalled = true
        _isSpeaking = false
    }

    /** Reset tracking for reuse. */
    fun reset() {
        spokenTexts.clear()
        stopCalled = false
        _isSpeaking = false
    }
}
