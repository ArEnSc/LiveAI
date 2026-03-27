package com.example.liveai.setup

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
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R
import com.example.liveai.agent.tts.PocketTtsProvider
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Builds the TTS tab content and owns the TTS lifecycle:
 * model loading, speech playback, and lip-sync wiring.
 */
class TtsTabBuilder(
    private val context: Context,
    private val glSurfaceView: () -> GLSurfaceView?,
    private val live2DManager: () -> LAppLive2DManager?
) {
    companion object {
        private const val TAG = "TtsTabBuilder"
    }

    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val padV = (12 * dp).toInt()
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)
    private val hintColor = ContextCompat.getColor(context, R.color.text_on_panel_hint)

    private var statusLabel: TextView? = null
    private var generateBtn: View? = null
    private var stopBtn: View? = null
    private var textInput: EditText? = null

    private var ttsProvider: PocketTtsProvider? = null
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun updateStatus(text: String) {
        statusLabel?.text = text
    }

    private fun setButtonStates(speaking: Boolean) {
        generateBtn?.isEnabled = !speaking
        generateBtn?.alpha = if (speaking) 0.4f else 1.0f
        stopBtn?.isEnabled = speaking
        stopBtn?.alpha = if (speaking) 1.0f else 0.4f
    }

    private fun speak(text: String) {
        ttsScope.launch {
            try {
                val provider = ttsProvider ?: run {
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

                    updateStatus("Models loaded in ${loadTime}ms")
                    p
                }

                setButtonStates(speaking = true)
                updateStatus("Speaking...")

                provider.speak(text)

                setButtonStates(speaking = false)
                updateStatus("Done")
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                setButtonStates(speaking = false)
                updateStatus("Error: ${e.message}")
            }
        }
    }

    private fun stop() {
        ttsProvider?.stop()
        setButtonStates(speaking = false)
        updateStatus("Stopped")
    }

    fun release() {
        ttsScope.cancel()
        ttsProvider?.release()
        ttsProvider = null
    }

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        // Label
        val label = TextView(context).apply {
            text = "Text to Speech"
            setTextColor(textOnPanel)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        content.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (8 * dp).toInt() })

        // Text input
        val input = EditText(context).apply {
            hint = "Type something to speak..."
            setHintTextColor(hintColor)
            setTextColor(textOnPanel)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_NONE
            minLines = 2
            maxLines = 4
            val bg = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(0x33FFFFFF)
                setStroke((1 * dp).toInt(), 0x55FFFFFF)
            }
            background = bg
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }
        textInput = input
        content.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * dp).toInt() })

        // Button row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val genBtn = SetupUiHelpers.makePillButton(
            context = context,
            label = "Speak",
            fillColor = 0xFF4CAF50.toInt(),
            textColor = textOnPanel,
            dp = dp
        ) {
            val text = textInput?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                speak(text)
            }
        }
        generateBtn = genBtn
        btnRow.addView(genBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = (8 * dp).toInt() })

        val stpBtn = SetupUiHelpers.makePillButton(
            context = context,
            label = "Stop",
            fillColor = Color.TRANSPARENT,
            textColor = textOnPanel,
            dp = dp
        ) { stop() }
        stopBtn = stpBtn
        stpBtn.isEnabled = false
        stpBtn.alpha = 0.4f
        btnRow.addView(stpBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))

        content.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * dp).toInt() })

        // Status label
        val status = TextView(context).apply {
            text = "Not initialized"
            setTextColor(dimWhite)
            textSize = 12f
        }
        statusLabel = status
        content.addView(status)

        return content
    }
}
