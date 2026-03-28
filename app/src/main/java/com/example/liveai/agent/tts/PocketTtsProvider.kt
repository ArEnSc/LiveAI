package com.example.liveai.agent.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.liveai.agent.AgentDebug
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
 */
class PocketTtsProvider(
    private val context: Context,
    private val voiceAssetPath: String = "voice/reference.wav",
    private val xnnpackHotPath: Boolean = true
) : TtsProvider {

    companion object {
        private const val TAG = "PocketTtsProvider"
        private const val BUFFER_FRAMES_BEFORE_PLAY = 1
        private const val SAMPLES_PER_FRAME = PocketTtsEngine.SAMPLES_PER_FRAME
        private const val SAMPLE_RATE = PocketTtsEngine.SAMPLE_RATE
    }

    // --- Initialization state ---

    private var engine: PocketTtsEngine? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var voiceAudio: FloatArray? = null
    private val initialized = AtomicBoolean(false)

    // --- Playback state ---

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val frameQueue = LinkedBlockingQueue<ShortArray>()
    private val speaking = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)

    // --- Lip sync: RMS timeline indexed by frame number ---
    // Generation thread appends; render thread reads via playbackHeadPosition.
    // 2000 frames × 80ms = ~160s max per utterance.

    private val rmsTimeline = FloatArray(2000)
    @Volatile private var rmsCount = 0

    // --- Frame pool: reuse ShortArrays to avoid per-frame allocation ---

    private val pcm16Buffer = ShortArray(SAMPLES_PER_FRAME)
    private val framePool = ArrayDeque<ShortArray>()

    private fun acquireFrame(): ShortArray =
        framePool.removeLastOrNull() ?: ShortArray(SAMPLES_PER_FRAME)

    private fun releaseFrame(frame: ShortArray) {
        framePool.addLast(frame)
    }

    // --- Public API ---

    override val isSpeaking: Boolean get() = speaking.get()

    /**
     * Current mouth openness (0.0–1.0) synced to actual audio playback position.
     * Read from the render loop to drive lip sync on a Live2D model.
     */
    val mouthVolume: Float
        get() {
            val track = audioTrack ?: return 0f
            val pos = try { track.playbackHeadPosition } catch (_: IllegalStateException) { return 0f }
            if (pos <= 0) return 0f
            val frameIndex = pos / SAMPLES_PER_FRAME
            val count = rmsCount
            return if (frameIndex in 0 until count) rmsTimeline[frameIndex] else 0f
        }

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

        stop()

        if (!initialized.get()) initialize()

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
            cleanupAudioTrack()
            speaking.set(false)
        }
    }

    override fun stop() {
        stopRequested.set(true)
        engine?.requestStop()
        frameQueue.clear()
        playbackThread?.interrupt()
        cleanupAudioTrack()
        speaking.set(false)
    }

    fun release() {
        stop()
        engine?.release()
        engine = null
        tokenizer = null
        voiceAudio = null
        initialized.set(false)
    }

    // --- Streaming generation pipeline ---

    private fun streamGenerate(
        engine: PocketTtsEngine,
        tokenizer: SentencePieceTokenizer,
        voiceAudio: FloatArray,
        text: String
    ) {
        val synthStart = System.currentTimeMillis()
        AgentDebug.log(TAG) { "tts: synthesis start (${text.length} chars)" }

        val track = createAudioTrack()
        audioTrack = track
        frameQueue.clear()
        rmsCount = 0
        var firstFrameLogged = false

        val drainThread = startPlaybackThread(track)

        val result = engine.generate(
            text = text,
            tokenizer = tokenizer,
            voiceAudio = voiceAudio,
            shouldContinue = { !stopRequested.get() },
            onFrame = { frame, _ ->
                if (stopRequested.get()) return@generate
                if (!firstFrameLogged) {
                    firstFrameLogged = true
                    AgentDebug.log(TAG) { "tts: first frame at +${System.currentTimeMillis() - synthStart}ms" }
                }
                enqueueFrame(frame)
            }
        )

        val synthDone = System.currentTimeMillis()
        AgentDebug.log(TAG) { "tts: synthesis done at +${synthDone - synthStart}ms (${result.metrics.audioDurationSec}s audio)" }

        // Signal end-of-stream and wait for playback to finish
        if (!stopRequested.get()) {
            frameQueue.put(SENTINEL)
        }
        try { drainThread.join() } catch (_: InterruptedException) {}
        playbackThread = null

        AgentDebug.log(TAG) { "tts: playback done at +${System.currentTimeMillis() - synthStart}ms" }

        lastMetrics = result.metrics
        Log.i(TAG, "Spoke ${result.metrics.audioDurationSec}s in ${result.metrics.totalTimeMs}ms " +
            "(${String.format("%.2f", result.metrics.realtimeFactor)}x RT)")
    }

    /**
     * Process a generated audio frame: compute lip sync RMS, convert to PCM16, and enqueue.
     */
    private fun enqueueFrame(frame: FloatArray) {
        AudioUtils.sanitize(frame)

        // Append RMS to timeline (volatile rmsCount write publishes the array write)
        val idx = rmsCount
        if (idx < rmsTimeline.size) {
            rmsTimeline[idx] = AudioUtils.rms(frame, gain = 3f)
            rmsCount = idx + 1
        }

        // Float → PCM16 conversion using pre-allocated buffer
        for (i in frame.indices) {
            pcm16Buffer[i] = (frame[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }

        val queued = acquireFrame()
        System.arraycopy(pcm16Buffer, 0, queued, 0, frame.size)
        frameQueue.put(queued)
    }

    // --- AudioTrack setup and teardown ---

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, SAMPLES_PER_FRAME * Short.SIZE_BYTES * 4)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun cleanupAudioTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        try { track.stop() } catch (_: IllegalStateException) {}
        track.release()
        try { playbackThread?.join(1000) } catch (_: InterruptedException) {}
        playbackThread = null
    }

    // --- Playback drain thread ---

    /**
     * Starts a thread that drains [frameQueue] into [track], triggers playback after
     * [BUFFER_FRAMES_BEFORE_PLAY] frames, then waits for the AudioTrack to finish
     * playing all written samples. Returns the started thread.
     */
    private fun startPlaybackThread(track: AudioTrack): Thread {
        val thread = Thread({
            var framesWritten = 0
            var totalSamplesWritten = 0
            var playbackStarted = false

            try {
                while (true) {
                    val frame = frameQueue.take()
                    if (frame === SENTINEL) break

                    val written = track.write(frame, 0, frame.size)
                    if (written > 0) {
                        totalSamplesWritten += written
                        framesWritten++
                    }
                    releaseFrame(frame)

                    if (!playbackStarted && framesWritten >= BUFFER_FRAMES_BEFORE_PLAY) {
                        AgentDebug.log(TAG) { "tts: AudioTrack.play() — audio out" }
                        track.play()
                        playbackStarted = true
                    }
                }
            } catch (_: InterruptedException) {
                return@Thread
            }

            if (!playbackStarted && framesWritten > 0) {
                track.play()
                playbackStarted = true
            }

            if (playbackStarted) {
                awaitPlaybackDrain(track, totalSamplesWritten)
            }
        }, "PocketTTS-Playback")

        playbackThread = thread
        thread.start()
        return thread
    }

    private fun awaitPlaybackDrain(track: AudioTrack, totalSamples: Int) {
        if (totalSamples <= 0) return
        val timeoutMs = (totalSamples * 1000L / SAMPLE_RATE) + 500
        val start = System.currentTimeMillis()
        try {
            while (!stopRequested.get()) {
                if (track.playbackHeadPosition >= totalSamples) break
                if (System.currentTimeMillis() - start > timeoutMs) break
                Thread.sleep(50)
            }
        } catch (_: InterruptedException) {}
    }
}

private val SENTINEL = ShortArray(0)
