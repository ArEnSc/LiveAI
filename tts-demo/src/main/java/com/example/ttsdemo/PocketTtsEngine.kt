package com.example.ttsdemo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Pocket TTS ONNX inference engine for Android.
 *
 * Pipeline: text + reference_audio → text_conditioner → flow_lm_main (AR loop)
 *           → flow_lm_flow (ODE solver) → mimi_decoder → audio
 *
 * Voice cloning: reference_audio → mimi_encoder → conditioning embeddings
 */
class PocketTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "PocketTtsEngine"
        const val SAMPLE_RATE = 24000
        const val SAMPLES_PER_FRAME = 1920
        const val LATENT_DIM = 32
        const val COND_DIM = 1024
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_LSD_STEPS = 2
        const val DEFAULT_EOS_THRESHOLD = -4.0f
        const val DEFAULT_MAX_FRAMES = 500
        const val DEFAULT_FRAMES_AFTER_EOS = 3
    }

    private lateinit var env: OrtEnvironment
    private lateinit var sessionOptions: OrtSession.SessionOptions
    private lateinit var textConditioner: OrtSession
    private lateinit var mimiEncoder: OrtSession
    private lateinit var flowLmMain: OrtSession
    private lateinit var flowLmFlow: OrtSession
    private lateinit var mimiDecoder: OrtSession

    var temperature: Float = DEFAULT_TEMPERATURE
    var lsdSteps: Int = DEFAULT_LSD_STEPS
    var eosThreshold: Float = DEFAULT_EOS_THRESHOLD

    data class PerformanceMetrics(
        val modelLoadTimeMs: Long = 0,
        val voiceEncodeTimeMs: Long = 0,
        val textConditionTimeMs: Long = 0,
        val generationTimeMs: Long = 0,
        val totalTimeMs: Long = 0,
        val framesGenerated: Int = 0,
        val audioDurationSec: Float = 0f,
        val realtimeFactor: Float = 0f,
        val peakMemoryMb: Float = 0f
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
        sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        // Extract all models to cache so we can use file-path sessions (memory-mapped)
        extractModelsToCacheIfNeeded()

        // Load all 5 models upfront to avoid per-generation load/unload overhead
        textConditioner = loadSessionFromFile("text_conditioner.onnx")
        mimiEncoder = loadSessionFromFile("mimi_encoder.onnx")
        flowLmMain = loadSessionFromFile("flow_lm_main_int8.onnx")
        flowLmFlow = loadSessionFromFile("flow_lm_flow_int8.onnx")
        mimiDecoder = loadSessionFromFile("mimi_decoder_int8.onnx")

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

    private fun loadSessionFromFile(filename: String): OrtSession {
        val modelPath = File(context.cacheDir, "onnx_models/$filename").absolutePath
        return env.createSession(modelPath, sessionOptions)
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
     * @param onFrame Callback for each generated audio frame (for streaming)
     */
    fun generate(
        text: String,
        tokenizer: SentencePieceTokenizer,
        voiceAudio: FloatArray,
        maxFrames: Int = DEFAULT_MAX_FRAMES,
        onFrame: ((FloatArray, Int) -> Unit)? = null
    ): GenerationResult {
        val totalStart = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        var peakMemory = 0L

        fun trackMemory() {
            val used = runtime.totalMemory() - runtime.freeMemory()
            if (used > peakMemory) peakMemory = used
        }

        // 1. Encode reference voice
        val voiceEncodeStart = System.currentTimeMillis()
        val voiceEmbeddings = encodeVoice(voiceAudio)
        val voiceEncodeTime = System.currentTimeMillis() - voiceEncodeStart
        Log.i(TAG, "Voice encoded in ${voiceEncodeTime}ms, shape: [1, ${voiceEmbeddings[0].size}, $COND_DIM]")
        trackMemory()

        // 2. Tokenize and condition text (load conditioner on-demand, release after)
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

        // 4. Voice conditioning pass (populates KV-cache)
        conditionFlowLm(flowState, voiceEmbeddings = voiceEmbeddings)
        trackMemory()

        // 5. Text conditioning pass (extends KV-cache)
        conditionFlowLm(flowState, textEmbeddings = textEmbeddings)
        trackMemory()

        // 6. Initialize mimi_decoder state
        val mimiState = initState(mimiDecoder)

        // 7. Autoregressive generation loop
        val genStart = System.currentTimeMillis()
        val allAudioFrames = mutableListOf<FloatArray>()
        var currentInput = createNanTensor(floatArrayOf(Float.NaN).let {
            FloatArray(LATENT_DIM) { Float.NaN }
        }) // BOS = NaN-filled [1, 1, 32]

        var eosStep: Int? = null
        var framesGenerated = 0

        for (step in 0 until maxFrames) {
            // Run flow_lm_main
            val (conditioning, eosLogit) = runFlowLmMain(flowState, currentInput)
            trackMemory()

            // Check EOS
            if (eosLogit > eosThreshold && eosStep == null) {
                eosStep = step
                Log.i(TAG, "EOS detected at step $step (logit=$eosLogit)")
            }
            if (eosStep != null && step >= eosStep + DEFAULT_FRAMES_AFTER_EOS) {
                Log.i(TAG, "Stopping at step $step ($DEFAULT_FRAMES_AFTER_EOS frames after EOS)")
                break
            }

            // Flow matching (ODE solver)
            val nextLatent = runFlowMatching(conditioning)
            trackMemory()

            // Decode audio frame
            val audioFrame = runMimiDecoder(mimiState, nextLatent)
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

        val metrics = PerformanceMetrics(
            voiceEncodeTimeMs = voiceEncodeTime,
            textConditionTimeMs = textCondTime,
            generationTimeMs = genTime,
            totalTimeMs = totalTime,
            framesGenerated = framesGenerated,
            audioDurationSec = audioDuration,
            realtimeFactor = rtfx,
            peakMemoryMb = peakMb
        )

        Log.i(TAG, "Generation complete: ${audioDuration}s audio in ${totalTime}ms (${rtfx}x realtime)")

        return GenerationResult(audio, metrics)
    }

    // --- Helpers ---

    /** Get the Nth output value from an OrtSession.Result */
    private fun OrtSession.Result.valueAt(index: Int): OnnxValue {
        val entries = this.toList()
        return entries[index].value
    }

    /**
     * Reshape a flat boolean array into a multi-dimensional Object array
     * that OnnxTensor.createTensor can accept.
     * ONNX Runtime Java requires Object arrays for boolean tensors.
     */
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

    // --- Pipeline Steps ---

    private fun encodeVoice(audio: FloatArray): Array<FloatArray> {
        val audioTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(audio),
            longArrayOf(1, 1, audio.size.toLong())
        )

        val result = mimiEncoder.run(mapOf("audio" to audioTensor))
        val outputTensor = result.valueAt(0) as OnnxTensor

        // Output shape: [1, T', 1024]
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

    /**
     * Initialize state tensors for a stateful ONNX model.
     * Reads input metadata to find state_* inputs and creates zero tensors.
     */
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
     * Update state map from model outputs.
     * Maps out_state_N outputs back to state_N inputs.
     */
    private fun updateState(
        state: MutableMap<String, OnnxTensor>,
        result: OrtSession.Result,
        session: OrtSession
    ) {
        val outputNames = session.outputInfo.keys.toList()
        for ((i, name) in outputNames.withIndex()) {
            if (name.startsWith("out_state_")) {
                val idx = name.removePrefix("out_state_").toInt()
                val stateKey = "state_$idx"
                // Close old tensor
                state[stateKey]?.close()
                // Copy the output tensor (result will be closed)
                val outputTensor = result.valueAt(i) as OnnxTensor
                state[stateKey] = cloneTensor(outputTensor)
            }
        }
    }

    private fun cloneTensor(tensor: OnnxTensor): OnnxTensor {
        val info = tensor.info
        val shape = info.shape

        return when (info.type) {
            ai.onnxruntime.OnnxJavaType.FLOAT -> {
                val srcBuffer = tensor.floatBuffer
                val newBuffer = FloatBuffer.allocate(srcBuffer.remaining())
                newBuffer.put(srcBuffer)
                newBuffer.flip()
                OnnxTensor.createTensor(env, newBuffer, shape)
            }
            ai.onnxruntime.OnnxJavaType.INT64 -> {
                val srcBuffer = tensor.longBuffer
                val newBuffer = LongBuffer.allocate(srcBuffer.remaining())
                newBuffer.put(srcBuffer)
                newBuffer.flip()
                OnnxTensor.createTensor(env, newBuffer, shape)
            }
            ai.onnxruntime.OnnxJavaType.BOOL -> {
                // Boolean tensors use getValue() which returns Object array
                val value = tensor.value
                OnnxTensor.createTensor(env, value)
            }
            ai.onnxruntime.OnnxJavaType.INT32 -> {
                val srcBuffer = tensor.intBuffer
                val newBuffer = java.nio.IntBuffer.allocate(srcBuffer.remaining())
                newBuffer.put(srcBuffer)
                newBuffer.flip()
                OnnxTensor.createTensor(env, newBuffer, shape)
            }
            else -> {
                Log.w(TAG, "cloneTensor: unhandled type ${info.type}, using getValue()")
                OnnxTensor.createTensor(env, tensor.value)
            }
        }
    }

    /**
     * Conditioning pass: feed voice or text embeddings into flow_lm_main
     * to populate the KV-cache. Outputs (conditioning, eos) are ignored.
     */
    private fun conditionFlowLm(
        state: MutableMap<String, OnnxTensor>,
        voiceEmbeddings: Array<FloatArray>? = null,
        textEmbeddings: Array<FloatArray>? = null
    ) {
        // Empty sequence [1, 0, 32]
        val emptySeq = OnnxTensor.createTensor(
            env,
            FloatBuffer.allocate(0),
            longArrayOf(1, 0, LATENT_DIM.toLong())
        )

        // Either voice or text embeddings, the other is empty [1, 0, 1024]
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
        updateState(state, result, flowLmMain)

        result.close()
        emptySeq.close()
        embTensor.close()
    }

    /**
     * Run one step of the AR loop: feed previous latent, get conditioning + EOS.
     * Returns (conditioning [1, dim], eosLogit scalar).
     */
    private fun runFlowLmMain(
        state: MutableMap<String, OnnxTensor>,
        latentInput: FloatArray
    ): Pair<FloatArray, Float> {
        // Sequence input [1, 1, 32]
        val seqTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(latentInput),
            longArrayOf(1, 1, LATENT_DIM.toLong())
        )

        // Empty text embeddings [1, 0, 1024]
        val emptyText = OnnxTensor.createTensor(
            env,
            FloatBuffer.allocate(0),
            longArrayOf(1, 0, COND_DIM.toLong())
        )

        val inputs = mutableMapOf<String, OnnxTensor>(
            "sequence" to seqTensor,
            "text_embeddings" to emptyText
        )
        inputs.putAll(state)

        val result = flowLmMain.run(inputs)

        // Output 0: conditioning [1, 1, dim] or [1, dim]
        val condTensor = result.valueAt(0) as OnnxTensor
        val condBuffer = condTensor.floatBuffer
        val condData = FloatArray(condBuffer.remaining())
        condBuffer.get(condData)

        // Output 1: eos_logit [1, 1]
        val eosTensor = result.valueAt(1) as OnnxTensor
        val eosLogit = eosTensor.floatBuffer.get(0)

        updateState(state, result, flowLmMain)

        result.close()
        seqTensor.close()
        emptyText.close()

        return condData to eosLogit
    }

    /**
     * Run the ODE solver (LSD decode) using flow_lm_flow.
     * Euler integration with configurable steps.
     */
    private fun runFlowMatching(conditioning: FloatArray): FloatArray {
        val dt = 1.0f / lsdSteps
        val std = if (temperature > 0) sqrt(temperature.toDouble()).toFloat() else 0f

        // Sample initial noise
        val rng = java.util.Random()
        val x = FloatArray(LATENT_DIM) {
            if (std > 0) (rng.nextGaussian() * std).toFloat() else 0f
        }

        for (j in 0 until lsdSteps) {
            val s = j.toFloat() / lsdSteps
            val t = s + dt

            val cTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(conditioning),
                longArrayOf(1, conditioning.size.toLong())
            )
            val sTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(s)),
                longArrayOf(1, 1)
            )
            val tTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(t)),
                longArrayOf(1, 1)
            )
            val xTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(x),
                longArrayOf(1, LATENT_DIM.toLong())
            )

            val result = flowLmFlow.run(mapOf(
                "c" to cTensor,
                "s" to sTensor,
                "t" to tTensor,
                "x" to xTensor
            ))

            val flowDir = (result.valueAt(0) as OnnxTensor).floatBuffer
            for (i in 0 until LATENT_DIM) {
                x[i] += flowDir.get(i) * dt
            }

            result.close()
            cTensor.close()
            sTensor.close()
            tTensor.close()
            xTensor.close()
        }

        return x
    }

    /**
     * Decode one latent frame to audio using mimi_decoder.
     */
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

        val audioTensor = result.valueAt(0) as OnnxTensor
        val audioBuffer = audioTensor.floatBuffer
        val audioData = FloatArray(audioBuffer.remaining())
        audioBuffer.get(audioData)

        updateState(state, result, mimiDecoder)

        result.close()
        latentTensor.close()

        return audioData
    }

    /**
     * Create a NaN-filled latent for BOS (beginning of sequence).
     */
    private fun createNanTensor(values: FloatArray): FloatArray = values

    /**
     * Prepare text for tokenization (matches Python preprocessing).
     */
    private fun prepareText(text: String): String {
        var result = text.trim()
        if (result.isEmpty()) return result

        // Capitalize first letter
        result = result.replaceFirstChar { it.uppercase() }

        // Add period if ends with alphanumeric
        if (result.last().isLetterOrDigit()) {
            result = "$result."
        }

        return result
    }

    /**
     * Resample audio from source sample rate to 24kHz.
     * Simple linear interpolation resampling.
     */
    fun resampleTo24kHz(audio: FloatArray, sourceSampleRate: Int): FloatArray {
        if (sourceSampleRate == SAMPLE_RATE) return audio

        val ratio = SAMPLE_RATE.toDouble() / sourceSampleRate
        val outputLen = (audio.size * ratio).toInt()
        val output = FloatArray(outputLen)

        for (i in 0 until outputLen) {
            val srcPos = i / ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()

            output[i] = if (srcIdx + 1 < audio.size) {
                audio[srcIdx] * (1f - frac) + audio[srcIdx + 1] * frac
            } else {
                audio[srcIdx.coerceAtMost(audio.size - 1)]
            }
        }

        return output
    }

    /**
     * Read a WAV file from assets and return (samples, sampleRate).
     * Supports 16-bit PCM mono/stereo.
     */
    fun readWavFromAssets(assetPath: String): Pair<FloatArray, Int> {
        val input = context.assets.open(assetPath)
        val data = input.readBytes()
        input.close()

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buf.position(0)
        val riff = ByteArray(4)
        buf.get(riff)
        require(String(riff) == "RIFF") { "Not a RIFF file" }

        buf.getInt() // file size
        val wave = ByteArray(4)
        buf.get(wave)
        require(String(wave) == "WAVE") { "Not a WAVE file" }

        var sampleRate = 0
        var numChannels = 0
        var bitsPerSample = 0
        var audioData: FloatArray? = null

        // Parse chunks
        while (buf.remaining() >= 8) {
            val chunkId = ByteArray(4)
            buf.get(chunkId)
            val chunkSize = buf.getInt()
            val chunkName = String(chunkId)

            when (chunkName) {
                "fmt " -> {
                    val format = buf.getShort().toInt() // PCM = 1
                    numChannels = buf.getShort().toInt()
                    sampleRate = buf.getInt()
                    buf.getInt() // byte rate
                    buf.getShort() // block align
                    bitsPerSample = buf.getShort().toInt()
                    // Skip extra fmt bytes
                    val remaining = chunkSize - 16
                    if (remaining > 0) buf.position(buf.position() + remaining)
                }
                "data" -> {
                    val numSamples = chunkSize / (bitsPerSample / 8)
                    val samples = FloatArray(numSamples)

                    when (bitsPerSample) {
                        16 -> {
                            for (i in 0 until numSamples) {
                                samples[i] = buf.getShort().toFloat() / 32768f
                            }
                        }
                        32 -> {
                            for (i in 0 until numSamples) {
                                samples[i] = buf.getFloat()
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported bits per sample: $bitsPerSample")
                    }

                    // Mix to mono if stereo
                    audioData = if (numChannels > 1) {
                        val monoLen = samples.size / numChannels
                        FloatArray(monoLen) { i ->
                            var sum = 0f
                            for (ch in 0 until numChannels) {
                                sum += samples[i * numChannels + ch]
                            }
                            sum / numChannels
                        }
                    } else {
                        samples
                    }
                }
                else -> {
                    // Skip unknown chunks
                    buf.position(buf.position() + chunkSize)
                }
            }
        }

        requireNotNull(audioData) { "No audio data found in WAV file" }
        return audioData to sampleRate
    }

    fun release() {
        if (this::textConditioner.isInitialized) textConditioner.close()
        if (this::mimiEncoder.isInitialized) mimiEncoder.close()
        if (this::flowLmMain.isInitialized) flowLmMain.close()
        if (this::flowLmFlow.isInitialized) flowLmFlow.close()
        if (this::mimiDecoder.isInitialized) mimiDecoder.close()
        if (this::env.isInitialized) env.close()
    }
}
