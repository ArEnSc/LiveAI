package com.example.liveai.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Pocket TTS ONNX inference engine for Android.
 *
 * Pipeline: text + reference_audio -> text_conditioner -> flow_lm_main (AR loop)
 *           -> flow_lm_flow (ODE solver) -> mimi_decoder -> audio
 *
 * Voice cloning: reference_audio -> mimi_encoder -> conditioning embeddings
 */
class PocketTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "PocketTtsEngine"
        const val SAMPLE_RATE = 24000
        const val SAMPLES_PER_FRAME = 1920
        const val LATENT_DIM = 32
        const val COND_DIM = 1024
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_LSD_STEPS = 1
        const val DEFAULT_EOS_THRESHOLD = -3.0f
        const val DEFAULT_MAX_FRAMES = 500
        const val DEFAULT_FRAMES_AFTER_EOS = 1
    }

    private lateinit var env: OrtEnvironment
    private lateinit var textConditioner: OrtSession
    private lateinit var mimiEncoder: OrtSession
    private lateinit var flowLmMain: OrtSession
    private lateinit var flowLmFlow: OrtSession
    private lateinit var mimiDecoder: OrtSession

    // Pre-allocated tensors reused every frame to avoid JNI alloc overhead
    private lateinit var reusableEmptyText: OnnxTensor    // [1, 0, 1024] — constant
    private val flowSeqBuffer = FloatArray(LATENT_DIM)    // reusable buffer for sequence input
    private val flowXBuffer = FloatArray(LATENT_DIM)      // reusable buffer for flow x input
    private val flowScalarS = floatArrayOf(0f)             // reusable [1,1] for s
    private val flowScalarT = floatArrayOf(0f)             // reusable [1,1] for t

    // Zero-copy state: hold previous OrtSession.Result alive so its output
    // tensors (used as next-step state inputs) remain valid without copying.
    private var flowLmPrevResult: OrtSession.Result? = null
    private var mimiPrevResult: OrtSession.Result? = null

    // Cached voice embeddings — mimi_encoder output for a given voiceAudio.
    // Avoids re-encoding the same reference voice on every generate() call.
    private var cachedVoiceAudio: FloatArray? = null
    private var cachedVoiceEmbeddings: Array<FloatArray>? = null

    var temperature: Float = DEFAULT_TEMPERATURE
    var lsdSteps: Int = DEFAULT_LSD_STEPS
    var eosThreshold: Float = DEFAULT_EOS_THRESHOLD
    var framesAfterEos: Int = DEFAULT_FRAMES_AFTER_EOS

    /**
     * Toggle XNNPACK for hot-path models (flow_lm_main, flow_lm_flow, mimi_decoder).
     * Set before calling [loadModels]. XNNPACK accelerates FP32 ops but may add
     * partitioning overhead for INT8 quantized models — benchmark with this off.
     */
    var useXnnpackForHotPath: Boolean = true

    data class PerformanceMetrics(
        val modelLoadTimeMs: Long = 0,
        val voiceEncodeTimeMs: Long = 0,
        val textConditionTimeMs: Long = 0,
        val generationTimeMs: Long = 0,
        val totalTimeMs: Long = 0,
        val framesGenerated: Int = 0,
        val audioDurationSec: Float = 0f,
        val realtimeFactor: Float = 0f,
        val peakMemoryMb: Float = 0f,
        val avgFlowLmMainMs: Float = 0f,
        val avgFlowMatchMs: Float = 0f,
        val avgMimiDecoderMs: Float = 0f
    )

    data class GenerationResult(
        val audio: FloatArray,
        val metrics: PerformanceMetrics
    )

    /**
     * Load all ONNX models from assets.
     * Models are extracted to cache dir for memory-mapped loading (avoids doubling
     * heap usage from readBytes). All 5 models are kept resident for fast generation.
     */
    fun loadModels(): Long {
        val startTime = System.currentTimeMillis()

        env = OrtEnvironment.getEnvironment()

        // Cold-path options: text_conditioner + mimi_encoder (run once per utterance)
        val coldOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            try {
                addXnnpack(mapOf("intra_op_num_threads" to "4"))
                Log.i(TAG, "XNNPACK enabled for cold-path models")
            } catch (e: Exception) {
                Log.w(TAG, "XNNPACK not available for cold-path: ${e.message}")
            }
        }

        // Hot-path options: flow_lm_main, flow_lm_flow, mimi_decoder (run every frame)
        // Let XNNPACK manage its own threadpool; set ORT threads to 1 to avoid contention
        val hotOptions = OrtSession.SessionOptions().apply {
            if (useXnnpackForHotPath) {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
                try {
                    addXnnpack(mapOf("intra_op_num_threads" to "4"))
                    Log.i(TAG, "XNNPACK enabled for hot-path models")
                } catch (e: Exception) {
                    Log.w(TAG, "XNNPACK not available for hot-path: ${e.message}")
                    setIntraOpNumThreads(4)
                }
            } else {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(1)
                Log.i(TAG, "XNNPACK disabled for hot-path models (CPU EP only)")
            }
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            addConfigEntry("session.intra_op.allow_spinning", "1")
        }

        // Extract all models to cache so we can use file-path sessions (memory-mapped)
        extractModelsToCacheIfNeeded()

        // Load all 5 models upfront to avoid per-generation load/unload overhead
        textConditioner = loadSessionFromFile("text_conditioner.onnx", coldOptions)
        mimiEncoder = loadSessionFromFile("mimi_encoder.onnx", coldOptions)
        flowLmMain = loadSessionFromFile("flow_lm_main_int8.onnx", hotOptions)
        flowLmFlow = loadSessionFromFile("flow_lm_flow_int8.onnx", hotOptions)
        mimiDecoder = loadSessionFromFile("mimi_decoder_int8.onnx", hotOptions)

        // Pre-allocate the constant empty-text tensor used every frame
        reusableEmptyText = OnnxTensor.createTensor(
            env,
            FloatBuffer.allocate(0),
            longArrayOf(1, 0, COND_DIM.toLong())
        )

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "All 5 models loaded in ${elapsed}ms")

        logModelInfo("text_conditioner", textConditioner)
        logModelInfo("mimi_encoder", mimiEncoder)
        logModelInfo("flow_lm_main", flowLmMain)
        logModelInfo("flow_lm_flow", flowLmFlow)
        logModelInfo("mimi_decoder", mimiDecoder)

        return elapsed
    }

    private fun extractModelsToCacheIfNeeded() {
        val modelDir = File(context.cacheDir, "onnx_models")
        modelDir.mkdirs()

        val modelFiles = listOf(
            "text_conditioner.onnx",
            "mimi_encoder.onnx",
            "flow_lm_main_int8.onnx",
            "flow_lm_flow_int8.onnx",
            "mimi_decoder_int8.onnx"
        )

        for (filename in modelFiles) {
            val outFile = File(modelDir, filename)
            if (outFile.exists()) continue
            Log.i(TAG, "Extracting $filename to cache...")
            context.assets.open("models/$filename").use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun loadSessionFromFile(filename: String, options: OrtSession.SessionOptions): OrtSession {
        val modelPath = File(context.cacheDir, "onnx_models/$filename").absolutePath
        return env.createSession(modelPath, options)
    }

    private fun logModelInfo(name: String, session: OrtSession) {
        Log.d(TAG, "--- $name ---")
        for (input in session.inputInfo) {
            Log.d(TAG, "  Input: ${input.key} -> ${input.value.info}")
        }
        for (output in session.outputInfo) {
            Log.d(TAG, "  Output: ${output.key} -> ${output.value.info}")
        }
    }

    /**
     * Generate speech from text with voice cloning.
     *
     * @param text Text to synthesize
     * @param tokenizer SentencePiece tokenizer
     * @param voiceAudio Reference voice audio as float array (mono, 24kHz)
     * @param maxFrames Maximum frames to generate
     * @param shouldContinue Called before each step; return false to stop early
     * @param onFrame Callback for each generated audio frame (for streaming)
     */
    fun generate(
        text: String,
        tokenizer: SentencePieceTokenizer,
        voiceAudio: FloatArray,
        maxFrames: Int = DEFAULT_MAX_FRAMES,
        shouldContinue: (() -> Boolean)? = null,
        onFrame: ((FloatArray, Int) -> Unit)? = null
    ): GenerationResult {
        val totalStart = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        var peakMemory = 0L

        fun trackMemory() {
            val used = runtime.totalMemory() - runtime.freeMemory()
            if (used > peakMemory) peakMemory = used
        }

        // 1. Encode reference voice (cached if same audio)
        val voiceEncodeStart = System.currentTimeMillis()
        val voiceEmbeddings = if (cachedVoiceAudio === voiceAudio && cachedVoiceEmbeddings != null) {
            Log.i(TAG, "Voice embeddings cached — skipping mimi_encoder")
            cachedVoiceEmbeddings!!
        } else {
            val emb = encodeVoice(voiceAudio)
            cachedVoiceAudio = voiceAudio
            cachedVoiceEmbeddings = emb
            emb
        }
        val voiceEncodeTime = System.currentTimeMillis() - voiceEncodeStart
        Log.i(TAG, "Voice encode step: ${voiceEncodeTime}ms")
        trackMemory()

        // 2. Tokenize and condition text
        val textCondStart = System.currentTimeMillis()
        val preparedText = prepareText(text)
        val tokenIds = tokenizer.encode(preparedText)
        Log.i(TAG, "Tokenized '$preparedText' -> ${tokenIds.size} tokens: ${tokenIds.toList()}")
        val textEmbeddings = runTextConditioner(tokenIds)
        val textCondTime = System.currentTimeMillis() - textCondStart
        Log.i(TAG, "Text conditioned in ${textCondTime}ms")
        trackMemory()

        // 3. Initialize flow_lm_main state
        val flowState = initState(flowLmMain)
        flowLmPrevResult = null

        // 4. Voice conditioning pass (populates KV-cache)
        conditionFlowLm(flowState, voiceEmbeddings = voiceEmbeddings)
        trackMemory()

        // 5. Text conditioning pass (extends KV-cache)
        conditionFlowLm(flowState, textEmbeddings = textEmbeddings)
        trackMemory()

        // 6. Initialize mimi_decoder state
        val mimiState = initState(mimiDecoder)
        mimiPrevResult = null

        // 7. Autoregressive generation loop
        val genStart = System.currentTimeMillis()
        val allAudioFrames = mutableListOf<FloatArray>()
        var currentInput = FloatArray(LATENT_DIM) { Float.NaN } // BOS = NaN-filled

        var eosStep: Int? = null
        var framesGenerated = 0
        var totalFlowLmMs = 0L
        var totalFlowMatchMs = 0L
        var totalDecoderMs = 0L

        for (step in 0 until maxFrames) {
            // Check cancellation
            if (shouldContinue?.invoke() == false) {
                Log.i(TAG, "Generation cancelled at step $step")
                break
            }

            // Run flow_lm_main
            var t0 = System.nanoTime()
            val (conditioning, eosLogit) = runFlowLmMain(flowState, currentInput)
            totalFlowLmMs += (System.nanoTime() - t0) / 1_000_000
            trackMemory()

            // Check EOS
            if (eosLogit > eosThreshold && eosStep == null) {
                eosStep = step
                Log.i(TAG, "EOS detected at step $step (logit=$eosLogit)")
            }
            if (eosStep != null && step >= eosStep + framesAfterEos) {
                Log.i(TAG, "Stopping at step $step ($framesAfterEos frames after EOS)")
                break
            }

            // Flow matching (ODE solver)
            t0 = System.nanoTime()
            val nextLatent = runFlowMatching(conditioning)
            totalFlowMatchMs += (System.nanoTime() - t0) / 1_000_000
            trackMemory()

            // Decode audio frame
            t0 = System.nanoTime()
            val audioFrame = runMimiDecoder(mimiState, nextLatent)
            totalDecoderMs += (System.nanoTime() - t0) / 1_000_000
            allAudioFrames.add(audioFrame)
            framesGenerated++
            trackMemory()

            // Callback for streaming
            onFrame?.invoke(audioFrame, step)

            // Prepare next input
            currentInput = nextLatent

            if (step % 10 == 0) {
                Log.d(TAG, "Step $step: generated ${audioFrame.size} samples")
            }
        }

        // Clean up held results from zero-copy state
        flowLmPrevResult?.close()
        flowLmPrevResult = null
        mimiPrevResult?.close()
        mimiPrevResult = null

        val genTime = System.currentTimeMillis() - genStart
        val totalTime = System.currentTimeMillis() - totalStart

        // Concatenate all audio
        val totalSamples = allAudioFrames.sumOf { it.size }
        val audio = FloatArray(totalSamples)
        var offset = 0
        for (frame in allAudioFrames) {
            frame.copyInto(audio, offset)
            offset += frame.size
        }

        val audioDuration = audio.size.toFloat() / SAMPLE_RATE
        val rtfx = if (totalTime > 0) audioDuration / (totalTime / 1000f) else 0f
        val peakMb = peakMemory / (1024f * 1024f)

        val n = if (framesGenerated > 0) framesGenerated.toFloat() else 1f
        val metrics = PerformanceMetrics(
            voiceEncodeTimeMs = voiceEncodeTime,
            textConditionTimeMs = textCondTime,
            generationTimeMs = genTime,
            totalTimeMs = totalTime,
            framesGenerated = framesGenerated,
            audioDurationSec = audioDuration,
            realtimeFactor = rtfx,
            peakMemoryMb = peakMb,
            avgFlowLmMainMs = totalFlowLmMs / n,
            avgFlowMatchMs = totalFlowMatchMs / n,
            avgMimiDecoderMs = totalDecoderMs / n
        )

        Log.i(TAG, "Generation complete: ${audioDuration}s audio in ${totalTime}ms (${rtfx}x realtime)")

        return GenerationResult(audio, metrics)
    }

    // --- Helpers ---

    private fun OrtSession.Result.valueAt(index: Int): OnnxValue {
        val entries = this.toList()
        return entries[index].value
    }

    private fun reshape(flat: BooleanArray, shape: LongArray): Any {
        if (shape.size == 1) return flat
        return reshapeRecursive(flat, shape, 0, 0).first
    }

    private fun reshapeRecursive(
        flat: BooleanArray, shape: LongArray, dim: Int, offset: Int
    ): Pair<Any, Int> {
        if (dim == shape.size - 1) {
            val len = shape[dim].toInt()
            val arr = BooleanArray(len) { i ->
                if (offset + i < flat.size) flat[offset + i] else false
            }
            return arr to (offset + len)
        }
        val len = shape[dim].toInt()
        val result = Array<Any>(len) { false }
        var off = offset
        for (i in 0 until len) {
            val (sub, newOff) = reshapeRecursive(flat, shape, dim + 1, off)
            result[i] = sub
            off = newOff
        }
        return result to off
    }

    /**
     * Pre-encode the reference voice so [generate] can skip the mimi_encoder step.
     * Call once after [loadModels] with the voice audio you'll be using.
     */
    fun preEncodeVoice(voiceAudio: FloatArray) {
        val start = System.currentTimeMillis()
        cachedVoiceEmbeddings = encodeVoice(voiceAudio)
        cachedVoiceAudio = voiceAudio
        Log.i(TAG, "Voice pre-encoded in ${System.currentTimeMillis() - start}ms")
    }

    // --- Pipeline Steps ---

    private fun encodeVoice(audio: FloatArray): Array<FloatArray> {
        val audioTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(audio),
            longArrayOf(1, 1, audio.size.toLong())
        )

        val result = mimiEncoder.run(mapOf("audio" to audioTensor))
        val outputTensor = result.valueAt(0) as OnnxTensor

        val shape = outputTensor.info.shape
        val flatData = outputTensor.floatBuffer
        val seqLen = shape[1].toInt()
        val dim = shape[2].toInt()

        val embeddings = Array(seqLen) { t ->
            FloatArray(dim) { d -> flatData.get(t * dim + d) }
        }

        audioTensor.close()
        result.close()

        return embeddings
    }

    private fun runTextConditioner(tokenIds: IntArray): Array<FloatArray> {
        val longIds = LongArray(tokenIds.size) { tokenIds[it].toLong() }
        val tokenTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longIds),
            longArrayOf(1, tokenIds.size.toLong())
        )

        val result = textConditioner.run(mapOf("token_ids" to tokenTensor))
        val outputTensor = result.valueAt(0) as OnnxTensor

        val shape = outputTensor.info.shape
        val flatData = outputTensor.floatBuffer
        val seqLen = shape[1].toInt()
        val dim = shape[2].toInt()

        val embeddings = Array(seqLen) { t ->
            FloatArray(dim) { d -> flatData.get(t * dim + d) }
        }

        tokenTensor.close()
        result.close()

        return embeddings
    }

    private fun initState(session: OrtSession): MutableMap<String, OnnxTensor> {
        val state = mutableMapOf<String, OnnxTensor>()

        for ((name, info) in session.inputInfo) {
            if (name.startsWith("state_")) {
                val nodeInfo = info.info as ai.onnxruntime.TensorInfo
                val shape = nodeInfo.shape.map { if (it < 0) 0L else it }.toLongArray()
                val totalElements = shape.fold(1L) { acc, s -> acc * s }

                when (nodeInfo.type) {
                    ai.onnxruntime.OnnxJavaType.FLOAT -> {
                        val buffer = FloatBuffer.allocate(totalElements.toInt())
                        state[name] = OnnxTensor.createTensor(env, buffer, shape)
                    }
                    ai.onnxruntime.OnnxJavaType.INT64 -> {
                        val buffer = LongBuffer.allocate(totalElements.toInt())
                        state[name] = OnnxTensor.createTensor(env, buffer, shape)
                    }
                    ai.onnxruntime.OnnxJavaType.BOOL -> {
                        val boolArray = BooleanArray(totalElements.toInt()) { false }
                        state[name] = OnnxTensor.createTensor(env, reshape(boolArray, shape))
                    }
                    ai.onnxruntime.OnnxJavaType.INT32 -> {
                        val buffer = java.nio.IntBuffer.allocate(totalElements.toInt())
                        state[name] = OnnxTensor.createTensor(env, buffer, shape)
                    }
                    else -> {
                        Log.w(TAG, "Unknown state type for $name: ${nodeInfo.type}, defaulting to float")
                        val buffer = FloatBuffer.allocate(totalElements.toInt())
                        state[name] = OnnxTensor.createTensor(env, buffer, shape)
                    }
                }
            }
        }

        Log.d(TAG, "Initialized ${state.size} state tensors for ${session.inputInfo.keys}")
        return state
    }

    /**
     * Zero-copy state update: point state map entries directly at the result's
     * output tensors and keep the result alive until the next call replaces it.
     * This avoids expensive per-frame buffer copies of the KV-cache.
     *
     * @param previousResult the result from the prior call — will be closed now
     *        that its tensors are no longer needed
     * @return the new result, which the caller must keep alive
     */
    private fun updateStateZeroCopy(
        state: MutableMap<String, OnnxTensor>,
        result: OrtSession.Result,
        session: OrtSession,
        previousResult: OrtSession.Result?
    ): OrtSession.Result {
        // Close the previous result — its output tensors were the old state entries
        previousResult?.close()

        val outputNames = session.outputInfo.keys.toList()
        for ((i, name) in outputNames.withIndex()) {
            if (name.startsWith("out_state_")) {
                val idx = name.removePrefix("out_state_").toInt()
                val stateKey = "state_$idx"
                // Point directly at the output tensor — no copy.
                // The tensor stays valid as long as we don't close `result`.
                state[stateKey] = result.valueAt(i) as OnnxTensor
            }
        }

        return result
    }

    private fun conditionFlowLm(
        state: MutableMap<String, OnnxTensor>,
        voiceEmbeddings: Array<FloatArray>? = null,
        textEmbeddings: Array<FloatArray>? = null
    ) {
        val emptySeq = OnnxTensor.createTensor(
            env,
            FloatBuffer.allocate(0),
            longArrayOf(1, 0, LATENT_DIM.toLong())
        )

        val embeddings = voiceEmbeddings ?: textEmbeddings
            ?: throw IllegalArgumentException("Must provide voice or text embeddings")

        val embFlat = FloatArray(embeddings.size * COND_DIM)
        for (t in embeddings.indices) {
            embeddings[t].copyInto(embFlat, t * COND_DIM)
        }
        val embTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(embFlat),
            longArrayOf(1, embeddings.size.toLong(), COND_DIM.toLong())
        )

        val inputs = mutableMapOf<String, OnnxTensor>(
            "sequence" to emptySeq,
            "text_embeddings" to embTensor
        )
        inputs.putAll(state)

        val result = flowLmMain.run(inputs)
        flowLmPrevResult = updateStateZeroCopy(state, result, flowLmMain, flowLmPrevResult)

        emptySeq.close()
        embTensor.close()
    }

    private fun runFlowLmMain(
        state: MutableMap<String, OnnxTensor>,
        latentInput: FloatArray
    ): Pair<FloatArray, Float> {
        // Reuse pre-allocated buffer — copy input data into it
        latentInput.copyInto(flowSeqBuffer)
        val seqTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(flowSeqBuffer),
            longArrayOf(1, 1, LATENT_DIM.toLong())
        )

        val inputs = mutableMapOf<String, OnnxTensor>(
            "sequence" to seqTensor,
            "text_embeddings" to reusableEmptyText
        )
        inputs.putAll(state)

        val result = flowLmMain.run(inputs)

        // Read outputs before handing result to zero-copy state update
        val condTensor = result.valueAt(0) as OnnxTensor
        val condBuffer = condTensor.floatBuffer
        val condData = FloatArray(condBuffer.remaining())
        condBuffer.get(condData)

        val eosTensor = result.valueAt(1) as OnnxTensor
        val eosLogit = eosTensor.floatBuffer.get(0)

        // Zero-copy: point state entries at result's output tensors,
        // close the previous result whose tensors are no longer needed
        flowLmPrevResult = updateStateZeroCopy(state, result, flowLmMain, flowLmPrevResult)

        seqTensor.close()

        return condData to eosLogit
    }

    // Persistent RNG avoids re-seeding each frame
    private val rng = java.util.Random()

    private fun runFlowMatching(conditioning: FloatArray): FloatArray {
        val dt = 1.0f / lsdSteps
        val std = if (temperature > 0) sqrt(temperature.toDouble()).toFloat() else 0f

        // Reuse pre-allocated buffer for x
        for (i in 0 until LATENT_DIM) {
            flowXBuffer[i] = if (std > 0) (rng.nextGaussian() * std).toFloat() else 0f
        }

        // Pre-wrap conditioning once (same data for all LSD steps)
        val cTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(conditioning),
            longArrayOf(1, conditioning.size.toLong())
        )

        for (j in 0 until lsdSteps) {
            flowScalarS[0] = j.toFloat() / lsdSteps
            flowScalarT[0] = flowScalarS[0] + dt

            val sTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(flowScalarS), longArrayOf(1, 1)
            )
            val tTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(flowScalarT), longArrayOf(1, 1)
            )
            val xTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(flowXBuffer), longArrayOf(1, LATENT_DIM.toLong())
            )

            val result = flowLmFlow.run(mapOf(
                "c" to cTensor,
                "s" to sTensor,
                "t" to tTensor,
                "x" to xTensor
            ))

            val flowDir = (result.valueAt(0) as OnnxTensor).floatBuffer
            for (i in 0 until LATENT_DIM) {
                flowXBuffer[i] += flowDir.get(i) * dt
            }

            result.close()
            sTensor.close()
            tTensor.close()
            xTensor.close()
        }

        cTensor.close()

        // Return a copy since flowXBuffer will be overwritten next frame
        return flowXBuffer.copyOf()
    }

    private fun runMimiDecoder(
        state: MutableMap<String, OnnxTensor>,
        latent: FloatArray
    ): FloatArray {
        val latentTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(latent),
            longArrayOf(1, 1, LATENT_DIM.toLong())
        )

        val inputs = mutableMapOf<String, OnnxTensor>("latent" to latentTensor)
        inputs.putAll(state)

        val result = mimiDecoder.run(inputs)

        // Read audio output before handing result to zero-copy state update
        val audioTensor = result.valueAt(0) as OnnxTensor
        val audioBuffer = audioTensor.floatBuffer
        val audioData = FloatArray(audioBuffer.remaining())
        audioBuffer.get(audioData)

        mimiPrevResult = updateStateZeroCopy(state, result, mimiDecoder, mimiPrevResult)

        latentTensor.close()

        return audioData
    }

    private fun prepareText(text: String): String {
        var result = text.trim()
        if (result.isEmpty()) return result

        result = result.replaceFirstChar { it.uppercase() }

        if (result.last().isLetterOrDigit()) {
            result = "$result."
        }

        return result
    }

    fun resampleTo24kHz(audio: FloatArray, sourceSampleRate: Int): FloatArray {
        return AudioUtils.resample(audio, sourceSampleRate, SAMPLE_RATE)
    }

    fun readWavFromAssets(assetPath: String): Pair<FloatArray, Int> {
        return AudioUtils.readWav(context.assets.open(assetPath))
    }

    fun release() {
        flowLmPrevResult?.close()
        flowLmPrevResult = null
        mimiPrevResult?.close()
        mimiPrevResult = null
        if (this::reusableEmptyText.isInitialized) reusableEmptyText.close()
        if (this::textConditioner.isInitialized) textConditioner.close()
        if (this::mimiEncoder.isInitialized) mimiEncoder.close()
        if (this::flowLmMain.isInitialized) flowLmMain.close()
        if (this::flowLmFlow.isInitialized) flowLmFlow.close()
        if (this::mimiDecoder.isInitialized) mimiDecoder.close()
        if (this::env.isInitialized) env.close()
    }
}
