package com.example.liveai.agent.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.liveai.tts.PocketTtsEngine
import com.example.liveai.tts.SentencePieceTokenizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * TtsProvider backed by the PocketTTS ONNX engine.
 *
 * Loads all models once on first [speak] call (or via explicit [initialize]).
 * Streams audio frame-by-frame through an AudioTrack during generation,
 * so playback begins before the full utterance is synthesized.
 *
 * Thread-safe: [stop] can be called from any thread to interrupt an
 * in-progress [speak].
 *
 * Requires these assets in the app module:
 *  - models/text_conditioner.onnx
 *  - models/mimi_encoder.onnx
 *  - models/flow_lm_main_int8.onnx
 *  - models/flow_lm_flow_int8.onnx
 *  - models/mimi_decoder_int8.onnx
 *  - tokenizer.model
 *  - voice/<voiceAssetPath>
 */
class PocketTtsProvider(
    private val context: Context,
    private val voiceAssetPath: String = "voice/reference.wav"
) : TtsProvider {

    companion object {
        private const val TAG = "PocketTtsProvider"
        private const val BUFFER_FRAMES_BEFORE_PLAY = 3
    }

    private var engine: PocketTtsEngine? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var voiceAudio: FloatArray? = null
    private var audioTrack: AudioTrack? = null

    private val speaking = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    override val isSpeaking: Boolean get() = speaking.get()

    /**
     * Current mouth openness derived from TTS audio amplitude.
     * Range 0.0 (closed) to 1.0 (fully open). Updated per generated frame.
     * Read this from the render loop to drive lip sync on a Live2D model.
     */
    @Volatile
    var mouthVolume: Float = 0f
        private set

    /**
     * Load ONNX models, tokenizer, and reference voice.
     * Safe to call multiple times — only the first call does work.
     * Called automatically by [speak] if not yet initialized.
     *
     * @return model load time in ms
     */
    suspend fun initialize(): Long = withContext(Dispatchers.IO) {
        if (initialized.get()) return@withContext 0L

        val eng = PocketTtsEngine(context)
        val loadTime = eng.loadModels()

        val tok = SentencePieceTokenizer.load(
            context.assets.open("tokenizer.model")
        )

        val (rawAudio, sampleRate) = eng.readWavFromAssets(voiceAssetPath)
        val voice = eng.resampleTo24kHz(rawAudio, sampleRate)
        Log.i(TAG, "Voice loaded: ${voice.size} samples (${voice.size / 24000f}s)")

        engine = eng
        tokenizer = tok
        voiceAudio = voice
        initialized.set(true)

        Log.i(TAG, "Initialized in ${loadTime}ms")
        loadTime
    }

    override suspend fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Stop any in-progress speech
        stop()

        // Ensure models are loaded
        if (!initialized.get()) {
            initialize()
        }

        val eng = engine ?: return
        val tok = tokenizer ?: return
        val voice = voiceAudio ?: return

        stopRequested.set(false)
        speaking.set(true)

        try {
            withContext(Dispatchers.IO) {
                streamGenerate(eng, tok, voice, trimmed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenerationStoppedException) {
            Log.i(TAG, "Speech stopped by request")
        } catch (e: Exception) {
            Log.e(TAG, "Speech failed", e)
        } finally {
            mouthVolume = 0f
            cleanupAudioTrack()
            speaking.set(false)
        }
    }

    override fun stop() {
        stopRequested.set(true)
        mouthVolume = 0f
        cleanupAudioTrack()
        speaking.set(false)
    }

    /**
     * Release all resources. The provider cannot be used after this.
     */
    fun release() {
        stop()
        engine?.release()
        engine = null
        tokenizer = null
        voiceAudio = null
        initialized.set(false)
    }

    private fun streamGenerate(
        engine: PocketTtsEngine,
        tokenizer: SentencePieceTokenizer,
        voiceAudio: FloatArray,
        text: String
    ) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            PocketTtsEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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

        var framesBuffered = 0
        var playbackStarted = false
        var totalSamplesWritten = 0

        val result = engine.generate(
            text = text,
            tokenizer = tokenizer,
            voiceAudio = voiceAudio,
            shouldContinue = { !stopRequested.get() },
            onFrame = { frame, _ ->
                if (stopRequested.get()) return@generate

                val sanitized = sanitizeFrame(frame)

                // Compute RMS for lip sync
                mouthVolume = computeRms(sanitized)

                val pcm16 = floatToPcm16(sanitized)
                track.write(pcm16, 0, pcm16.size)
                totalSamplesWritten += pcm16.size
                framesBuffered++

                if (!playbackStarted && framesBuffered >= BUFFER_FRAMES_BEFORE_PLAY) {
                    track.play()
                    playbackStarted = true
                }
            }
        )

        if (stopRequested.get()) return

        // Start playback if we finished before hitting the buffer threshold
        if (!playbackStarted && framesBuffered > 0) {
            track.play()
            playbackStarted = true
        }

        // Wait for AudioTrack to finish draining
        if (playbackStarted && totalSamplesWritten > 0) {
            waitForPlaybackDrain(track, totalSamplesWritten)
        }

        Log.i(TAG, "Spoke ${result.metrics.audioDurationSec}s in ${result.metrics.totalTimeMs}ms " +
            "(${String.format("%.2f", result.metrics.realtimeFactor)}x RT)")
    }

    private fun waitForPlaybackDrain(track: AudioTrack, totalSamples: Int) {
        val maxWaitMs = (totalSamples * 1000L / PocketTtsEngine.SAMPLE_RATE) + 500
        val startWait = System.currentTimeMillis()

        while (!stopRequested.get()) {
            val headPosition = track.playbackHeadPosition
            if (headPosition >= totalSamples) break
            if (System.currentTimeMillis() - startWait > maxWaitMs) break
            Thread.sleep(50)
        }
    }

    private fun cleanupAudioTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.stop()
        } catch (_: IllegalStateException) {
            // Already stopped
        }
        track.release()
    }

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

    private fun floatToPcm16(audio: FloatArray): ShortArray {
        return ShortArray(audio.size) { i ->
            (audio[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /**
     * Compute RMS amplitude of a frame, clamped to [0, 1].
     * Scaled up by 3x so typical speech maps to a visible mouth range.
     */
    private fun computeRms(frame: FloatArray): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0f
        for (sample in frame) {
            sum += sample * sample
        }
        val rms = sqrt(sum / frame.size)
        return (rms * 3f).coerceIn(0f, 1f)
    }

    private class GenerationStoppedException : RuntimeException("Generation stopped")
}
