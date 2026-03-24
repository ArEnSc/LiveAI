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

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition not available on this device")
                _state.value = SpeechState.Error("Speech recognition not available")
                return null
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
        return recognizer
    }

    fun startListening() {
        val rec = ensureRecognizer() ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _state.value = SpeechState.Listening()
        rec.startListening(intent)
        Log.d(TAG, "Started listening")
    }

    fun stopListening() {
        recognizer?.stopListening()
        Log.d(TAG, "Stopped listening")
    }

    fun resetToIdle() {
        _state.value = SpeechState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _state.value = SpeechState.Idle
    }

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could expose for amplitude visualization later
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
        }

        override fun onError(error: Int) {
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
            Log.e(TAG, "Speech error: $message (code=$error)")
            _state.value = SpeechState.Error(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty().trim()
            Log.d(TAG, "Final result: $text")
            if (text.isNotEmpty()) {
                _state.value = SpeechState.Result(text)
            } else {
                _state.value = SpeechState.Error("No speech detected")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
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
