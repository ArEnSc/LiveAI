package com.example.liveai.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SpeechState {
    data object Idle : SpeechState
    data class Listening(val partialText: String = "") : SpeechState
    data class Result(val finalText: String) : SpeechState
    data class Error(val message: String) : SpeechState
}

/**
 * Wraps Android's SpeechRecognizer for push-to-talk usage.
 * Must be created and used on the main thread.
 */
class SpeechRecognizerManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveAI-Speech"
    }

    private var recognizer: SpeechRecognizer? = null

    /** Monotonic session counter — stale callbacks from old sessions are ignored. */
    private var sessionId = 0L
    private var activeSessionId = -1L

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private fun createRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            _state.value = SpeechState.Error("Speech recognition not available")
            return null
        }
        return SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }
    }

    fun startListening() {
        // Destroy previous recognizer to prevent stale callbacks
        recognizer?.destroy()
        recognizer = null

        // Increment session BEFORE creating listener so it captures the correct ID
        val currentSession = ++sessionId
        activeSessionId = currentSession

        val rec = createRecognizer() ?: return
        recognizer = rec

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                500L
            )
        }

        _state.value = SpeechState.Listening()
        rec.startListening(intent)
        Log.d(TAG, "Started listening (session=$currentSession)")
    }

    fun stopListening() {
        recognizer?.stopListening()
        Log.d(TAG, "Stopped listening (session=$activeSessionId)")
    }

    fun resetToIdle() {
        _state.value = SpeechState.Idle
    }

    fun destroy() {
        activeSessionId = -1L
        recognizer?.destroy()
        recognizer = null
        _state.value = SpeechState.Idle
    }

    /**
     * Creates a new listener that captures the current [sessionId].
     * Callbacks are ignored if the session has changed since creation.
     */
    private fun createListener(): RecognitionListener {
        val listenerId = sessionId
        return object : RecognitionListener {

            private fun isStale(): Boolean = listenerId != activeSessionId

            override fun onReadyForSpeech(params: Bundle?) {
                if (isStale()) return
                Log.d(TAG, "Ready for speech (session=$listenerId)")
            }

            override fun onBeginningOfSpeech() {
                if (isStale()) return
                Log.d(TAG, "Speech began (session=$listenerId)")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Could expose for amplitude visualization later
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (isStale()) return
                Log.d(TAG, "Speech ended (session=$listenerId)")
            }

            override fun onError(error: Int) {
                if (isStale()) {
                    Log.d(TAG, "Ignoring stale error (session=$listenerId, active=$activeSessionId)")
                    return
                }
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    else -> "Recognition error ($error)"
                }
                Log.e(TAG, "Speech error: $message (code=$error, session=$listenerId)")
                _state.value = SpeechState.Error(message)
            }

            override fun onResults(results: Bundle?) {
                if (isStale()) {
                    Log.d(TAG, "Ignoring stale result (session=$listenerId, active=$activeSessionId)")
                    return
                }
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty().trim()
                Log.d(TAG, "Final result: $text (session=$listenerId)")
                if (text.isNotEmpty()) {
                    _state.value = SpeechState.Result(text)
                } else {
                    _state.value = SpeechState.Error("No speech detected")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isStale()) return
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull().orEmpty()
                if (partial.isNotEmpty()) {
                    _state.value = SpeechState.Listening(partialText = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
