package com.example.liveai.setup

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R
import com.example.liveai.audio.AudioVolumeSource

/**
 * Builds the Audio Motion tab content.
 */
class AudioTabBuilder(
    private val context: Context,
    private val audioVolumeSourceProvider: () -> AudioVolumeSource?,
    initialEnabled: Boolean,
    initialIntensity: Float,
    initialSpeed: Float,
    private val onEnabledChanged: (Boolean) -> Unit,
    private val onIntensityChanged: (Float) -> Unit,
    private val onSpeedChanged: (Float) -> Unit
) {
    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val padV = (12 * dp).toInt()
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)

    private var enabled = initialEnabled
    private var intensity = initialIntensity
    private var speed = initialSpeed

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        val audioRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val audioSwitch = Switch(context).apply {
            text = "Enabled"
            setTextColor(dimWhite)
            textSize = 13f
            isChecked = enabled
            setOnCheckedChangeListener { _, checked ->
                enabled = checked
                onEnabledChanged(checked)
            }
        }
        audioRow.addView(audioSwitch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val volumeMeter = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        audioRow.addView(volumeMeter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val volumeLabel = TextView(context).apply {
            text = "0%"
            setTextColor(dimWhite)
            textSize = 12f
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        audioRow.addView(volumeLabel)
        content.addView(audioRow)

        val volumeHandler = Handler(Looper.getMainLooper())
        val volumeUpdater = object : Runnable {
            override fun run() {
                val vol = ((audioVolumeSourceProvider()?.volume ?: 0f) * 100).toInt()
                volumeMeter.progress = vol
                volumeLabel.text = "${vol}%"
                volumeHandler.postDelayed(this, 100)
            }
        }
        volumeHandler.post(volumeUpdater)

        val intSlider = SetupUiHelpers.makeSliderRow(
            context = context,
            label = "Intensity",
            initialValue = intensity,
            formatValue = { "%.1f".format(it) },
            min = 0.0f,
            max = 3.0f,
            steps = 300,
            dp = dp,
            dimWhite = dimWhite,
            textOnPanel = textOnPanel
        ) { value, _ ->
            intensity = value
            onIntensityChanged(value)
        }
        content.addView(intSlider)

        val spdSlider = SetupUiHelpers.makeSliderRow(
            context = context,
            label = "Speed",
            initialValue = speed,
            formatValue = { "%.1f".format(it) },
            min = 0.0f,
            max = 3.0f,
            steps = 300,
            dp = dp,
            dimWhite = dimWhite,
            textOnPanel = textOnPanel
        ) { value, _ ->
            speed = value
            onSpeedChanged(value)
        }
        content.addView(spdSlider)

        return content
    }
}
