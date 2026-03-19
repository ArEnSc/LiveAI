package com.example.ttsdemo

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TtsDemoUiState {
    data object Initial : TtsDemoUiState
    data class Loading(val status: String) : TtsDemoUiState
    data class Ready(
        val text: String = "Something feels off. Please, all of you, go ahead without me. I'll join you later.",
        val isGenerating: Boolean = false,
        val generationLog: List<String> = emptyList(),
        val metrics: PocketTtsEngine.PerformanceMetrics? = null,
        val hasAudio: Boolean = false,
        val isPlaying: Boolean = false,
        val systemMemoryMb: Float = 0f,
        val appMemoryMb: Float = 0f
    ) : TtsDemoUiState
    data class Error(val message: String) : TtsDemoUiState
}

sealed interface TtsDemoUiEvent {
    data class TextChanged(val text: String) : TtsDemoUiEvent
    data object Generate : TtsDemoUiEvent
    data object PlayAudio : TtsDemoUiEvent
    data object StopAudio : TtsDemoUiEvent
    data class LsdStepsChanged(val steps: Int) : TtsDemoUiEvent
    data class TemperatureChanged(val temp: Float) : TtsDemoUiEvent
}

class TtsDemoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TtsDemoVM"
    }

    private val _uiState = MutableStateFlow<TtsDemoUiState>(TtsDemoUiState.Initial)
    val uiState: StateFlow<TtsDemoUiState> = _uiState.asStateFlow()

    private var engine: PocketTtsEngine? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var voiceAudio: FloatArray? = null
    private var generatedAudio: FloatArray? = null
    private var audioTrack: AudioTrack? = null

    init {
        loadEngine()
    }

    fun onEvent(event: TtsDemoUiEvent) {
        when (event) {
            is TtsDemoUiEvent.TextChanged -> updateText(event.text)
            is TtsDemoUiEvent.Generate -> generate()
            is TtsDemoUiEvent.PlayAudio -> playAudio()
            is TtsDemoUiEvent.StopAudio -> stopAudio()
            is TtsDemoUiEvent.LsdStepsChanged -> {
                engine?.lsdSteps = event.steps
            }
            is TtsDemoUiEvent.TemperatureChanged -> {
                engine?.temperature = event.temp
            }
        }
    }

    private fun updateText(text: String) {
        val state = _uiState.value
        if (state is TtsDemoUiState.Ready) {
            _uiState.value = state.copy(text = text)
        }
    }

    private fun loadEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                _uiState.value = TtsDemoUiState.Loading("Loading tokenizer...")
                tokenizer = SentencePieceTokenizer.load(
                    context.assets.open("tokenizer.model")
                )
                Log.i(TAG, "Tokenizer loaded")

                _uiState.value = TtsDemoUiState.Loading("Loading ONNX models (5 models, ~200MB)...")
                engine = PocketTtsEngine(context)
                val loadTime = engine!!.loadModels()

                _uiState.value = TtsDemoUiState.Loading("Loading reference voice...")
                val (rawAudio, sampleRate) = engine!!.readWavFromAssets("voice/reference.wav")
                voiceAudio = engine!!.resampleTo24kHz(rawAudio, sampleRate)
                Log.i(TAG, "Voice loaded: ${voiceAudio!!.size} samples at 24kHz (${voiceAudio!!.size / 24000f}s)")

                val memInfo = getMemoryInfo(context)
                _uiState.value = TtsDemoUiState.Ready(
                    generationLog = listOf(
                        "Models loaded in ${loadTime}ms",
                        "Voice: ${voiceAudio!!.size / 24000f}s at 24kHz (resampled from ${sampleRate}Hz)",
                        "Ready to generate!"
                    ),
                    systemMemoryMb = memInfo.first,
                    appMemoryMb = memInfo.second
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load engine", e)
                _uiState.value = TtsDemoUiState.Error("Failed to load: ${e.message}")
            }
        }
    }

    private fun generate() {
        val state = _uiState.value
        if (state !is TtsDemoUiState.Ready || state.isGenerating) return
        val text = state.text.trim()
        if (text.isEmpty()) return

        val eng = engine ?: return
        val tok = tokenizer ?: return
        val voice = voiceAudio ?: return

        _uiState.value = state.copy(
            isGenerating = true,
            generationLog = listOf("Starting generation..."),
            metrics = null,
            hasAudio = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val log = mutableListOf<String>()
                log.add("Text: \"$text\"")
                log.add("LSD steps: ${eng.lsdSteps}, Temp: ${eng.temperature}")
                updateLog(log)

                val result = eng.generate(
                    text = text,
                    tokenizer = tok,
                    voiceAudio = voice,
                    onFrame = { _, step ->
                        if (step % 5 == 0) {
                            log.add("Frame $step generated...")
                            updateLog(log)
                        }
                    }
                )

                generatedAudio = result.audio
                log.add("---")
                log.add("Done! ${result.metrics.framesGenerated} frames")
                log.add("Audio: ${result.metrics.audioDurationSec}s")
                log.add("Total time: ${result.metrics.totalTimeMs}ms")
                log.add("RTFx: ${String.format("%.2f", result.metrics.realtimeFactor)}x")

                val memInfo = getMemoryInfo(getApplication())

                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) {
                        current.copy(
                            isGenerating = false,
                            generationLog = log.toList(),
                            metrics = result.metrics,
                            hasAudio = true,
                            systemMemoryMb = memInfo.first,
                            appMemoryMb = memInfo.second
                        )
                    } else current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) {
                        current.copy(
                            isGenerating = false,
                            generationLog = listOf("ERROR: ${e.message}", e.stackTraceToString().take(500))
                        )
                    } else current
                }
            }
        }
    }

    private fun updateLog(log: List<String>) {
        _uiState.update { current ->
            if (current is TtsDemoUiState.Ready) {
                current.copy(generationLog = log.toList())
            } else current
        }
    }

    private fun playAudio() {
        val rawAudio = generatedAudio ?: return
        stopAudio()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) current.copy(isPlaying = true) else current
                }

                // Sanitize audio: replace NaN/Inf and clamp to [-1.0, 1.0]
                val audio = sanitizeAudio(rawAudio)
                if (audio.isEmpty()) {
                    Log.w(TAG, "No valid audio samples to play")
                    _uiState.update { current ->
                        if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                    }
                    return@launch
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    PocketTtsEngine.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )

                if (bufferSize <= 0) {
                    Log.e(TAG, "Invalid buffer size from getMinBufferSize: $bufferSize")
                    _uiState.update { current ->
                        if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                    }
                    return@launch
                }

                // Use at least the min buffer size, but allow larger for smoother playback
                val actualBufferSize = maxOf(bufferSize, audio.size * Float.SIZE_BYTES)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(PocketTtsEngine.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(actualBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                val written = audioTrack?.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING) ?: 0
                Log.i(TAG, "AudioTrack wrote $written / ${audio.size} samples")

                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                }

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback failed", e)
                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                }
            }
        }
    }

    /**
     * Sanitize audio samples: replace NaN/Inf with 0, normalize if peak > 1.0,
     * and clamp to [-1.0, 1.0].
     */
    private fun sanitizeAudio(audio: FloatArray): FloatArray {
        val sanitized = FloatArray(audio.size)
        var nanCount = 0
        var maxAbs = 0f

        for (i in audio.indices) {
            val sample = audio[i]
            if (sample.isNaN() || sample.isInfinite()) {
                sanitized[i] = 0f
                nanCount++
            } else {
                sanitized[i] = sample
                val abs = kotlin.math.abs(sample)
                if (abs > maxAbs) maxAbs = abs
            }
        }

        if (nanCount > 0) {
            Log.w(TAG, "Replaced $nanCount NaN/Inf samples out of ${audio.size}")
        }

        // Normalize if peak exceeds 1.0
        if (maxAbs > 1f) {
            Log.i(TAG, "Normalizing audio: peak=$maxAbs")
            for (i in sanitized.indices) {
                sanitized[i] = sanitized[i] / maxAbs
            }
        }

        Log.i(TAG, "Audio stats: ${audio.size} samples, peak=$maxAbs, nanCount=$nanCount")
        return sanitized
    }

    private fun stopAudio() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _uiState.update { current ->
            if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
        }
    }

    private fun getMemoryInfo(context: Context): Pair<Float, Float> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val appUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val systemAvailMb = memInfo.availMem / (1024f * 1024f)

        return systemAvailMb to appUsedMb
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
        engine?.release()
    }
}
