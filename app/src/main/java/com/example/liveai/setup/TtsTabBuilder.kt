package com.example.liveai.setup

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.example.liveai.agent.tts.PocketTtsProvider
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppModel
import com.example.liveai.tts.PocketTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Builds the TTS tab content and owns the TTS lifecycle:
 * model loading, speech playback, lip-sync wiring, and benchmark display.
 */
class TtsTabBuilder(
    private val context: Context,
    private val glSurfaceView: () -> GLSurfaceView?,
    private val live2DManager: () -> LAppLive2DManager?
) {
    companion object {
        private const val TAG = "TtsTabBuilder"
    }

    private val t = PanelTheme.from(context)

    private var statusLabel: TextView? = null
    private var generateBtn: View? = null
    private var stopBtn: View? = null
    private var textInput: EditText? = null

    // Metrics display
    private var metricsContainer: LinearLayout? = null
    private var rtfLabel: TextView? = null
    private var totalTimeLabel: TextView? = null
    private var voiceEncLabel: TextView? = null
    private var textCondLabel: TextView? = null
    private var genTimeLabel: TextView? = null
    private var framesLabel: TextView? = null
    private var audioDurLabel: TextView? = null
    private var peakMemLabel: TextView? = null
    private var sysMemLabel: TextView? = null
    private var appMemLabel: TextView? = null

    // Param labels
    private var lsdLabel: TextView? = null
    private var tempLabel: TextView? = null
    private var eosLabel: TextView? = null
    private var framesAfterEosLabel: TextView? = null

    // Per-frame metrics
    private var avgFlowLmLabel: TextView? = null
    private var avgFlowMatchLabel: TextView? = null
    private var avgDecoderLabel: TextView? = null
    private var avgTotalFrameLabel: TextView? = null

    // Log
    private var logContainer: LinearLayout? = null

    private var ttsProvider: PocketTtsProvider? = null
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val logLines = mutableListOf<String>()

    private fun updateStatus(text: String) {
        statusLabel?.text = text
    }

    private fun setButtonStates(speaking: Boolean) {
        generateBtn?.isEnabled = !speaking
        generateBtn?.alpha = if (speaking) 0.4f else 1.0f
        stopBtn?.isEnabled = speaking
        stopBtn?.alpha = if (speaking) 1.0f else 0.4f
    }

    private fun appendLog(line: String) {
        logLines.add(line)
        refreshLog()
    }

    private fun clearLog() {
        logLines.clear()
        refreshLog()
    }

    private fun refreshLog() {
        val container = logContainer ?: return
        container.removeAllViews()
        for (line in logLines.takeLast(20)) {
            container.addView(makeSmallText(line, 0xB4FFFFFF.toInt()))
        }
    }

    private fun speak(text: String) {
        ttsScope.launch {
            try {
                val provider = ttsProvider ?: run {
                    clearLog()
                    appendLog("Loading models...")
                    updateStatus("Loading models...")
                    setButtonStates(speaking = true)
                    val p = PocketTtsProvider(context)
                    val loadTime = p.initialize()
                    ttsProvider = p

                    // Connect mouth volume to Live2D model
                    val volumeSource = LAppModel.MouthVolumeSource { p.mouthVolume }
                    glSurfaceView()?.queueEvent {
                        live2DManager()?.setMouthVolumeSource(volumeSource)
                    }

                    appendLog("Models loaded in ${loadTime}ms")
                    updateStatus("Ready")
                    p
                }

                clearLog()
                appendLog("Text: \"$text\"")
                val eng = provider.engineRef
                appendLog("LSD: ${eng?.lsdSteps}, Temp: ${eng?.temperature}, EOS: ${eng?.eosThreshold}")
                appendLog("Mode: STREAMING")

                setButtonStates(speaking = true)
                updateStatus("Speaking...")

                provider.speak(text)

                setButtonStates(speaking = false)

                // Show metrics
                val m = provider.lastMetrics
                if (m != null) {
                    appendLog("---")
                    appendLog("${m.framesGenerated} frames, ${String.format("%.2f", m.audioDurationSec)}s audio")
                    appendLog("Total: ${m.totalTimeMs}ms | RTFx: ${String.format("%.2f", m.realtimeFactor)}x")
                    showMetrics(m)
                    updateStatus("Done — ${String.format("%.2f", m.realtimeFactor)}x realtime")
                } else {
                    updateStatus("Done")
                }

                showMemory()
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                setButtonStates(speaking = false)
                appendLog("ERROR: ${e.message}")
                updateStatus("Error: ${e.message}")
            }
        }
    }

    private fun stop() {
        ttsProvider?.stop()
        setButtonStates(speaking = false)
        appendLog("Stopped")
        updateStatus("Stopped")
    }

    fun release() {
        ttsScope.cancel()
        ttsProvider?.release()
        ttsProvider = null
    }

    // --- Metrics display ---

    private fun showMetrics(m: PocketTtsEngine.PerformanceMetrics) {
        metricsContainer?.visibility = View.VISIBLE
        rtfLabel?.text = "${String.format("%.2f", m.realtimeFactor)}x"
        totalTimeLabel?.text = "${m.totalTimeMs}ms"
        voiceEncLabel?.text = "${m.voiceEncodeTimeMs}ms"
        textCondLabel?.text = "${m.textConditionTimeMs}ms"
        genTimeLabel?.text = "${m.generationTimeMs}ms"
        framesLabel?.text = "${m.framesGenerated}"
        audioDurLabel?.text = "${String.format("%.2f", m.audioDurationSec)}s"
        peakMemLabel?.text = "${String.format("%.1f", m.peakMemoryMb)} MB"

        val totalPerFrame = m.avgFlowLmMainMs + m.avgFlowMatchMs + m.avgMimiDecoderMs
        avgFlowLmLabel?.text = "${String.format("%.1f", m.avgFlowLmMainMs)}ms"
        avgFlowMatchLabel?.text = "${String.format("%.1f", m.avgFlowMatchMs)}ms"
        avgDecoderLabel?.text = "${String.format("%.1f", m.avgMimiDecoderMs)}ms"
        avgTotalFrameLabel?.text = "${String.format("%.1f", totalPerFrame)}ms"
    }

    private fun showMemory() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val runtime = Runtime.getRuntime()
        val appMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val sysMb = memInfo.availMem / (1024f * 1024f)

        sysMemLabel?.text = "${String.format("%.0f", sysMb)} MB"
        appMemLabel?.text = "${String.format("%.1f", appMb)} MB"
    }

    // --- Build UI ---

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(t.padH, t.padV, t.padH, t.padV)
            visibility = View.GONE
        }

        // Title
        content.addView(makeBoldLabel("Text to Speech"))

        // Text input
        val input = EditText(context).apply {
            hint = "Type something to speak..."
            setHintTextColor(t.hintColor)
            setTextColor(t.textOnPanel)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_NONE
            minLines = 2
            maxLines = 4
            background = GradientDrawable().apply {
                cornerRadius = 8 * t.dp
                setColor(0x33FFFFFF)
                setStroke((1 * t.dp).toInt(), 0x55FFFFFF)
            }
            setPadding((12 * t.dp).toInt(), (8 * t.dp).toInt(), (12 * t.dp).toInt(), (8 * t.dp).toInt())
        }
        textInput = input
        content.addView(input, marginLp(bottom = 10))

        // --- Parameter controls ---
        content.addView(makeBoldLabel("Parameters", size = 12f))

        // LSD Steps slider
        val lsdRow = makeSliderRow("LSD Steps", 1f, 1f, 10f, 9) { value ->
            val steps = value.toInt()
            ttsProvider?.engineRef?.lsdSteps = steps
            lsdLabel?.text = "LSD Steps: $steps"
        }
        lsdLabel = lsdRow.first
        content.addView(lsdRow.second, marginLp(bottom = 4))

        // Temperature slider
        val tempRow = makeSliderRow("Temp", 0.7f, 0f, 1.5f, 150) { value ->
            ttsProvider?.engineRef?.temperature = value
            tempLabel?.text = "Temp: ${String.format("%.2f", value)}"
        }
        tempLabel = tempRow.first
        content.addView(tempRow.second, marginLp(bottom = 4))

        // EOS Threshold slider
        val eosRow = makeSliderRow("EOS Threshold", -3.0f, -6f, 0f, 60) { value ->
            ttsProvider?.engineRef?.eosThreshold = value
            eosLabel?.text = "EOS Threshold: ${String.format("%.1f", value)}"
        }
        eosLabel = eosRow.first
        content.addView(eosRow.second, marginLp(bottom = 4))

        // Frames After EOS slider
        val faeRow = makeSliderRow("Frames After EOS", 1f, 0f, 5f, 5) { value ->
            ttsProvider?.engineRef?.framesAfterEos = value.toInt()
            framesAfterEosLabel?.text = "Frames After EOS: ${value.toInt()}"
        }
        framesAfterEosLabel = faeRow.first
        content.addView(faeRow.second, marginLp(bottom = 10))

        // --- Buttons ---
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val genBtn = SetupUiHelpers.makePillButton(
            context, "Speak", 0xFF4CAF50.toInt(), t.textOnPanel, t.dp
        ) {
            val text = textInput?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) speak(text)
        }
        generateBtn = genBtn
        btnRow.addView(genBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = (8 * t.dp).toInt() })

        val stpBtn = SetupUiHelpers.makePillButton(
            context, "Stop", Color.TRANSPARENT, t.textOnPanel, t.dp
        ) { stop() }
        stopBtn = stpBtn
        stpBtn.isEnabled = false
        stpBtn.alpha = 0.4f
        btnRow.addView(stpBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        content.addView(btnRow, marginLp(bottom = 8))

        // Status
        val status = makeSmallText("Not initialized", t.dimWhite)
        statusLabel = status
        content.addView(status, marginLp(bottom = 10))

        // --- Metrics card ---
        val metricsCard = makeCard().apply { visibility = View.GONE }
        metricsContainer = metricsCard

        metricsCard.addView(makeBoldLabel("Performance", size = 12f))
        rtfLabel = addMetricRow(metricsCard, "RTFx")
        totalTimeLabel = addMetricRow(metricsCard, "Total time")
        voiceEncLabel = addMetricRow(metricsCard, "Voice encode")
        textCondLabel = addMetricRow(metricsCard, "Text condition")
        genTimeLabel = addMetricRow(metricsCard, "Generation")
        framesLabel = addMetricRow(metricsCard, "Frames")
        audioDurLabel = addMetricRow(metricsCard, "Audio duration")
        peakMemLabel = addMetricRow(metricsCard, "Peak memory")

        metricsCard.addView(makeBoldLabel("Per-Frame Avg", size = 11f))
        avgFlowLmLabel = addMetricRow(metricsCard, "flow_lm_main")
        avgFlowMatchLabel = addMetricRow(metricsCard, "flow_lm_flow")
        avgDecoderLabel = addMetricRow(metricsCard, "mimi_decoder")
        avgTotalFrameLabel = addMetricRow(metricsCard, "Total/frame")

        content.addView(metricsCard, marginLp(bottom = 8))

        // --- Memory card ---
        val memCard = makeCard()
        memCard.addView(makeBoldLabel("Memory", size = 12f))
        sysMemLabel = addMetricRow(memCard, "System avail")
        appMemLabel = addMetricRow(memCard, "App heap")
        content.addView(memCard, marginLp(bottom = 8))

        // --- Log ---
        content.addView(makeBoldLabel("Log", size = 12f))
        val logBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        logContainer = logBox
        content.addView(logBox)

        return content
    }

    // --- View helpers ---

    private fun makeBoldLabel(text: String, size: Float = 14f): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(t.textOnPanel)
            textSize = size
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (4 * t.dp).toInt())
        }
    }

    private fun makeSmallText(text: String, color: Int = t.dimWhite): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 8 * t.dp
                setColor(0x22FFFFFF)
            }
            setPadding((10 * t.dp).toInt(), (8 * t.dp).toInt(), (10 * t.dp).toInt(), (8 * t.dp).toInt())
        }
    }

    private fun addMetricRow(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labelTv = TextView(context).apply {
            text = label
            setTextColor(t.dimWhite)
            textSize = 11f
        }
        row.addView(labelTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val valueTv = TextView(context).apply {
            text = "—"
            setTextColor(t.textOnPanel)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }
        row.addView(valueTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        parent.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (2 * t.dp).toInt() })

        return valueTv
    }

    private fun makeSliderRow(
        label: String,
        initial: Float,
        min: Float,
        max: Float,
        steps: Int,
        onChange: (Float) -> Unit
    ): Pair<TextView, LinearLayout> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val labelTv = TextView(context).apply {
            text = if (steps <= 10) "$label: ${initial.toInt()}" else "$label: ${String.format("%.2f", initial)}"
            setTextColor(t.dimWhite)
            textSize = 11f
        }
        row.addView(labelTv)

        val seekBar = SeekBar(context).apply {
            this.max = steps
            progress = ((initial - min) / (max - min) * steps).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = min + (progress.toFloat() / steps) * (max - min)
                    onChange(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return labelTv to row
    }

    private fun marginLp(bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (bottom * t.dp).toInt() }
    }
}
