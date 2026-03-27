package com.example.liveai.gyroscope

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Reads accelerometer data and provides smoothed pitch/roll tilt angles
 * normalized to roughly -1.0 to 1.0. Thread-safe reads via volatile.
 *
 * Uses gravity (accelerometer) rather than raw gyroscope to avoid
 * drift and integration complexity.
 */
class GyroscopeTiltSource(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "GyroscopeTiltSource"
        private const val SMOOTHING = 0.85f
    }

    @Volatile
    var tiltX: Float = 0f
        private set

    @Volatile
    var tiltY: Float = 0f
        private set

    private var sensorManager: SensorManager? = null
    private var isRunning = false

    // Reference orientation captured on start — tilt is relative to this
    private var refPitch: Float = 0f
    private var refRoll: Float = 0f
    private var hasReference = false
    private var refSampleCount = 0

    fun start() {
        if (isRunning) return

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sm == null) {
            Log.w(TAG, "SensorManager not available")
            return
        }
        sensorManager = sm

        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer sensor available")
            return
        }

        hasReference = false
        refSampleCount = 0
        refPitch = 0f
        refRoll = 0f

        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isRunning = true
        Log.d(TAG, "Tilt sensor started")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager?.unregisterListener(this)
        isRunning = false
        tiltX = 0f
        tiltY = 0f
        Log.d(TAG, "Tilt sensor stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Compute pitch and roll from gravity vector
        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (norm < 0.1f) return

        val pitch = atan2(ay, az) // tilt forward/back
        val roll = atan2(ax, az)  // tilt left/right

        // Capture the reference orientation from the first few samples
        if (!hasReference) {
            refPitch += pitch
            refRoll += roll
            refSampleCount++
            if (refSampleCount >= 10) {
                refPitch /= refSampleCount
                refRoll /= refSampleCount
                hasReference = true
                Log.d(TAG, "Reference captured: pitch=$refPitch roll=$refRoll")
            }
            return
        }

        // Delta from reference, normalized so ~45 degrees = 1.0
        val deltaPitch = (pitch - refPitch) / (Math.PI.toFloat() / 4f)
        val deltaRoll = (roll - refRoll) / (Math.PI.toFloat() / 4f)

        // Smooth
        tiltX = tiltX * SMOOTHING + deltaRoll * (1f - SMOOTHING)
        tiltY = tiltY * SMOOTHING + deltaPitch * (1f - SMOOTHING)

        // Clamp
        tiltX = tiltX.coerceIn(-1f, 1f)
        tiltY = tiltY.coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
