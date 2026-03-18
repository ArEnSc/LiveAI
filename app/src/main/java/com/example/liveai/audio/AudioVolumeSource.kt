package com.example.liveai.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Reads microphone input and provides a normalized RMS volume (0.0 - 1.0).
 * Runs on a background thread. Thread-safe volume reads via volatile.
 */
class AudioVolumeSource(private val context: Context) {

    companion object {
        private const val TAG = "AudioVolumeSource"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile
    var volume: Float = 0f
        private set

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRunning = false

    fun start() {
        if (isRunning) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted, skipping mic")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            isRunning = true
            audioRecord?.startRecording()

            recordingThread = Thread({
                val buffer = ShortArray(bufferSize / 2)
                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        volume = computeRms(buffer, read)
                    }
                }
            }, "AudioVolumeSource").apply {
                isDaemon = true
                start()
            }

            Log.d(TAG, "Mic recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stop()
        }
    }

    fun stop() {
        isRunning = false
        try {
            recordingThread?.join(500)
        } catch (_: InterruptedException) {}
        recordingThread = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
        volume = 0f
        Log.d(TAG, "Mic recording stopped")
    }

    /**
     * Compute RMS volume normalized to 0.0 - 1.0.
     * 16-bit PCM range is -32768..32767, so we normalize by 32768.
     */
    private fun computeRms(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble() / 32768.0
            sum += sample * sample
        }
        return sqrt(sum / length).toFloat().coerceIn(0f, 1f)
    }
}
