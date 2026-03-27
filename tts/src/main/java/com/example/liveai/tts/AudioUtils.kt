package com.example.liveai.tts

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Shared audio utilities for WAV I/O, resampling, and PCM conversion.
 */
object AudioUtils {

    /**
     * Parse a WAV file from an [InputStream] into mono float samples and sample rate.
     * Supports 16-bit and 32-bit float PCM. Multi-channel audio is mixed to mono.
     */
    fun readWav(input: InputStream): Pair<FloatArray, Int> {
        val data = input.readBytes()
        input.close()

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        buf.position(0)
        val riff = ByteArray(4)
        buf.get(riff)
        require(String(riff) == "RIFF") { "Not a RIFF file" }

        buf.getInt()
        val wave = ByteArray(4)
        buf.get(wave)
        require(String(wave) == "WAVE") { "Not a WAVE file" }

        var sampleRate = 0
        var numChannels = 0
        var bitsPerSample = 0
        var audioData: FloatArray? = null

        while (buf.remaining() >= 8) {
            val chunkId = ByteArray(4)
            buf.get(chunkId)
            val chunkSize = buf.getInt()
            val chunkName = String(chunkId)

            when (chunkName) {
                "fmt " -> {
                    buf.getShort() // format
                    numChannels = buf.getShort().toInt()
                    sampleRate = buf.getInt()
                    buf.getInt() // byte rate
                    buf.getShort() // block align
                    bitsPerSample = buf.getShort().toInt()
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
                    buf.position(buf.position() + chunkSize)
                }
            }
        }

        requireNotNull(audioData) { "No audio data found in WAV file" }
        return audioData to sampleRate
    }

    /**
     * Resample audio to [targetRate] Hz using linear interpolation.
     * Returns the input unchanged if already at [targetRate].
     */
    fun resample(audio: FloatArray, sourceSampleRate: Int, targetRate: Int): FloatArray {
        if (sourceSampleRate == targetRate) return audio

        val ratio = targetRate.toDouble() / sourceSampleRate
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
     * Clamp float samples to [-1, 1], replacing NaN/Infinity with 0.
     */
    fun sanitize(frame: FloatArray): FloatArray {
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

    /**
     * Convert normalized float audio [-1, 1] to 16-bit PCM.
     */
    fun floatToPcm16(audio: FloatArray): ShortArray {
        return ShortArray(audio.size) { i ->
            (audio[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /**
     * Compute RMS amplitude of a frame, scaled by [gain] and clamped to [0, 1].
     */
    fun rms(frame: FloatArray, gain: Float = 1f): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0f
        for (sample in frame) {
            sum += sample * sample
        }
        val rms = sqrt(sum / frame.size)
        return (rms * gain).coerceIn(0f, 1f)
    }
}
