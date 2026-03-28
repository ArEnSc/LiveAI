package com.example.liveai.agent.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.liveai.tts.AudioUtils
import com.example.liveai.tts.PocketTtsEngine
import com.example.liveai.tts.SentencePieceTokenizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

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
    private val voiceAssetPath: String = "voice/reference.wav",
    private val xnnpackHotPath: Boolean = true
) : TtsProvider {

    companion object {
        private const val TAG = "PocketTtsProvider"
        private const val BUFFER_FRAMES_BEFORE_PLAY = 1
    }

    private var engine: PocketTtsEngine? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var voiceAudio: FloatArray? = null
    private var audioTrack: AudioTrack? = null

    // Pre-allocated PCM buffer reused every frame to avoid per-frame allocation
    private val pcm16Buffer = ShortArray(PocketTtsEngine.SAMPLES_PER_FRAME)

    // Producer-consumer queue: generation thread enqueues, playback thread drains
    private val frameQueue = LinkedBlockingQueue<ShortArray>()
    private var playbackThread: Thread? = null

    // Pool of ShortArrays to avoid per-frame allocation for the queue
    private val framePool = ArrayDeque<ShortArray>()

    private fun acquireFrame(): ShortArray =
        framePool.removeLastOrNull() ?: ShortArray(PocketTtsEngine.SAMPLES_PER_FRAME)

    private fun releaseFrame(frame: ShortArray) {
        framePool.addLast(frame)
    }

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

    /** Metrics from the most recent [speak] call. */
    @Volatile
    var lastMetrics: PocketTtsEngine.PerformanceMetrics? = null
        private set

    /** Access the underlying engine for tuning (lsdSteps, temperature). Null until initialized. */
    val engineRef: PocketTtsEngine? get() = engine

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
        eng.useXnnpackForHotPath = xnnpackHotPath
        val loadTime = eng.loadModels()

        val tok = SentencePieceTokenizer.load(
            context.assets.open("tokenizer.model")
        )

        val (rawAudio, sampleRate) = eng.readWavFromAssets(voiceAssetPath)
        val voice = eng.resampleTo24kHz(rawAudio, sampleRate)
        Log.i(TAG, "Voice loaded: ${voice.size} samples (${voice.size / 24000f}s)")

        // Pre-encode voice now so generate() never has to
        eng.preEncodeVoice(voice)

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
        engine?.requestStop()
        mouthVolume = 0f
        frameQueue.clear()
        playbackThread?.interrupt()
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
        frameQueue.clear()

        // Start playback drain thread
        val sentinel = ShortArray(0)
        val drainThread = Thread({
            var framesWritten = 0
            var totalSamplesWritten = 0
            var playbackStarted = false

            try {
                while (true) {
                    val frame = frameQueue.take()
                    if (frame.isEmpty()) break // sentinel

                    val written = track.write(frame, 0, frame.size)
                    if (written > 0) {
                        totalSamplesWritten += written
                        framesWritten++
                    }
                    releaseFrame(frame)

                    if (!playbackStarted && framesWritten >= BUFFER_FRAMES_BEFORE_PLAY) {
                        track.play()
                        playbackStarted = true
                    }
                }
            } catch (_: InterruptedException) {
                // stop() interrupted us — exit cleanly
                return@Thread
            }

            // Start playback if generation finished before buffer threshold
            if (!playbackStarted && framesWritten > 0) {
                track.play()
                playbackStarted = true
            }

            // Wait for AudioTrack to drain remaining samples
            if (playbackStarted && totalSamplesWritten > 0) {
                val maxWaitMs = (totalSamplesWritten * 1000L / PocketTtsEngine.SAMPLE_RATE) + 500
                val startWait = System.currentTimeMillis()
                try {
                    while (!stopRequested.get()) {
                        val headPosition = track.playbackHeadPosition
                        if (headPosition >= totalSamplesWritten) break
                        if (System.currentTimeMillis() - startWait > maxWaitMs) break
                        Thread.sleep(50)
                    }
                } catch (_: InterruptedException) {
                    // stop() interrupted drain wait
                }
            }
        }, "PocketTTS-Playback")
        playbackThread = drainThread
        drainThread.start()

        // Generation thread: produce frames into the queue
        val result = engine.generate(
            text = text,
            tokenizer = tokenizer,
            voiceAudio = voiceAudio,
            shouldContinue = { !stopRequested.get() },
            onFrame = { frame, _ ->
                if (stopRequested.get()) return@generate

                // Sanitize in-place (no allocation)
                AudioUtils.sanitize(frame)

                // Compute RMS for lip sync (on generation thread so it tracks generation, not playback)
                mouthVolume = AudioUtils.rms(frame, gain = 3f)

                // Convert to PCM16 using pre-allocated buffer
                val len = frame.size
                for (i in 0 until len) {
                    pcm16Buffer[i] = (frame[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }

                // Copy into pooled frame and enqueue (pcm16Buffer is reused)
                val queued = acquireFrame()
                System.arraycopy(pcm16Buffer, 0, queued, 0, len)
                frameQueue.put(queued)
            }
        )

        // Signal drain thread that generation is done
        if (!stopRequested.get()) {
            frameQueue.put(sentinel)
        }

        // Wait for playback to finish draining
        try {
            drainThread.join()
        } catch (_: InterruptedException) {
            // stop() interrupted join
        }
        playbackThread = null

        lastMetrics = result.metrics
        Log.i(TAG, "Spoke ${result.metrics.audioDurationSec}s in ${result.metrics.totalTimeMs}ms " +
            "(${String.format("%.2f", result.metrics.realtimeFactor)}x RT)")
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

        // Ensure playback thread has exited
        try {
            playbackThread?.join(1000)
        } catch (_: InterruptedException) {
            // Ignored
        }
        playbackThread = null
    }

}
