package com.example.ttsdemo

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        val savedFilePath: String? = null,
        val systemMemoryMb: Float = 0f,
        val appMemoryMb: Float = 0f
    ) : TtsDemoUiState
    data class Error(val message: String) : TtsDemoUiState
}

sealed interface TtsDemoUiEvent {
    data class TextChanged(val text: String) : TtsDemoUiEvent
    data object Generate : TtsDemoUiEvent
    data object StreamGenerate : TtsDemoUiEvent
    data object PlayAudio : TtsDemoUiEvent
    data object StopAudio : TtsDemoUiEvent
    data object SaveWav : TtsDemoUiEvent
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
            is TtsDemoUiEvent.StreamGenerate -> streamGenerate()
            is TtsDemoUiEvent.PlayAudio -> playAudio()
            is TtsDemoUiEvent.StopAudio -> stopAudio()
            is TtsDemoUiEvent.SaveWav -> saveWav()
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

    private fun streamGenerate() {
        val state = _uiState.value
        if (state !is TtsDemoUiState.Ready || state.isGenerating) return
        val text = state.text.trim()
        if (text.isEmpty()) return

        val eng = engine ?: return
        val tok = tokenizer ?: return
        val voice = voiceAudio ?: return

        stopAudio()

        _uiState.value = state.copy(
            isGenerating = true,
            isPlaying = true,
            generationLog = listOf("Starting stream generation..."),
            metrics = null,
            hasAudio = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val log = mutableListOf<String>()
                log.add("Text: \"$text\"")
                log.add("LSD steps: ${eng.lsdSteps}, Temp: ${eng.temperature}")
                log.add("Mode: STREAMING")
                updateLog(log)

                // Create a MODE_STREAM AudioTrack with a reasonable buffer
                val minBufferSize = AudioTrack.getMinBufferSize(
                    PocketTtsEngine.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                // Use 4x min buffer to avoid underruns
                val bufferSize = maxOf(
                    minBufferSize,
                    PocketTtsEngine.SAMPLES_PER_FRAME * Short.SIZE_BYTES * 4
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(PocketTtsEngine.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track

                val bufferFrames = 3
                var framesBuffered = 0
                var playbackStarted = false
                val allFrames = mutableListOf<FloatArray>()

                val result = eng.generate(
                    text = text,
                    tokenizer = tok,
                    voiceAudio = voice,
                    onFrame = { frame, step ->
                        val sanitized = sanitizeFrame(frame)
                        allFrames.add(sanitized)

                        // Convert to PCM16 and write to stream
                        val pcm16 = ShortArray(sanitized.size) { i ->
                            (sanitized[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        }
                        track.write(pcm16, 0, pcm16.size)

                        framesBuffered++

                        // Start playback after buffering a few frames
                        if (!playbackStarted && framesBuffered >= bufferFrames) {
                            track.play()
                            playbackStarted = true
                            log.add("Playback started at frame $step (~${bufferFrames * 80}ms latency)")
                            updateLog(log)
                        }

                        if (step % 10 == 0) {
                            log.add("Frame $step streaming...")
                            updateLog(log)
                        }
                    }
                )

                // Start playback if we finished before hitting the buffer threshold
                if (!playbackStarted && framesBuffered > 0) {
                    track.play()
                }

                // Concatenate all frames for replay/save
                val totalSamples = allFrames.sumOf { it.size }
                val fullAudio = FloatArray(totalSamples)
                var offset = 0
                for (frame in allFrames) {
                    frame.copyInto(fullAudio, offset)
                    offset += frame.size
                }
                generatedAudio = fullAudio

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
                            isPlaying = true,
                            generationLog = log.toList(),
                            metrics = result.metrics,
                            hasAudio = true,
                            systemMemoryMb = memInfo.first,
                            appMemoryMb = memInfo.second
                        )
                    } else current
                }

                // Wait for AudioTrack to finish draining, then clean up
                val remainingSamples = totalSamples - track.playbackHeadPosition
                if (remainingSamples > 0) {
                    val remainingMs = (remainingSamples * 1000L) / PocketTtsEngine.SAMPLE_RATE
                    kotlinx.coroutines.delay(remainingMs + 100)
                }

                track.stop()
                track.release()
                if (audioTrack === track) audioTrack = null

                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream generation failed", e)
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) {
                        current.copy(
                            isGenerating = false,
                            isPlaying = false,
                            generationLog = listOf("ERROR: ${e.message}", e.stackTraceToString().take(500))
                        )
                    } else current
                }
            }
        }
    }

    /**
     * Sanitize a single audio frame: replace NaN/Inf with 0, clamp to [-1, 1].
     */
    private fun sanitizeFrame(frame: FloatArray): FloatArray {
        val sanitized = FloatArray(frame.size)
        for (i in frame.indices) {
            val sample = frame[i]
            sanitized[i] = if (sample.isNaN() || sample.isInfinite()) {
                0f
            } else {
                sample.coerceIn(-1f, 1f)
            }
        }
        return sanitized
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

                // Sanitize and convert to 16-bit PCM
                val floatAudio = sanitizeAudio(rawAudio)
                if (floatAudio.isEmpty()) {
                    Log.w(TAG, "No valid audio samples to play")
                    _uiState.update { current ->
                        if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                    }
                    return@launch
                }

                // Convert float [-1.0, 1.0] to short [-32768, 32767]
                val pcm16 = ShortArray(floatAudio.size) { i ->
                    (floatAudio[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }

                val bufferSizeBytes = pcm16.size * Short.SIZE_BYTES

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(PocketTtsEngine.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack = track

                val written = track.write(pcm16, 0, pcm16.size)
                Log.i(TAG, "AudioTrack wrote $written / ${pcm16.size} samples (${bufferSizeBytes} bytes)")

                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    _uiState.update { current ->
                        if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                    }
                    return@launch
                }

                // Use notification marker to detect playback completion
                track.notificationMarkerPosition = pcm16.size
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        Log.i(TAG, "Playback complete")
                        t?.stop()
                        t?.release()
                        if (audioTrack === t) audioTrack = null
                        _uiState.update { current ->
                            if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                        }
                    }
                    override fun onPeriodicNotification(t: AudioTrack?) {}
                })

                track.play()
                Log.i(TAG, "AudioTrack playing: state=${track.playState}, sampleRate=${track.sampleRate}")
            } catch (e: Exception) {
                Log.e(TAG, "Playback failed", e)
                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) current.copy(isPlaying = false) else current
                }
            }
        }
    }

    /**
     * Sanitize audio samples: replace NaN/Inf with 0, normalize if peak > 1.0.
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

    private fun saveWav() {
        val rawAudio = generatedAudio ?: return
        val audio = sanitizeAudio(rawAudio)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val timestamp = System.currentTimeMillis()
                val file = File(downloadsDir, "tts_output_$timestamp.wav")

                writeWav(file, audio, PocketTtsEngine.SAMPLE_RATE)

                Log.i(TAG, "Saved WAV to ${file.absolutePath}")
                _uiState.update { current ->
                    if (current is TtsDemoUiState.Ready) {
                        current.copy(savedFilePath = file.absolutePath)
                    } else current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save WAV", e)
            }
        }
    }

    private fun writeWav(file: File, samples: FloatArray, sampleRate: Int) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * blockAlign

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())

            // fmt chunk
            header.put("fmt ".toByteArray())
            header.putInt(16)               // chunk size
            header.putShort(1)              // PCM format
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())

            // data chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Write samples as 16-bit PCM
            val buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val s = (sample * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                buf.putShort(s)
                if (buf.position() == buf.capacity()) {
                    fos.write(buf.array(), 0, buf.position())
                    buf.clear()
                }
            }
            if (buf.position() > 0) {
                fos.write(buf.array(), 0, buf.position())
            }
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
