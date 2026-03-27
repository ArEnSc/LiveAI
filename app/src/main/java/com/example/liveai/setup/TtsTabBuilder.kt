package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R

/**
 * Builds the TTS tab content: text input, generate/stop buttons, status.
 */
class TtsTabBuilder(
    private val context: Context,
    private val onGenerate: (String) -> Unit,
    private val onStop: () -> Unit,
    private val isSpeaking: () -> Boolean,
    private val isInitialized: () -> Boolean
) {
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

    fun updateStatus(text: String) {
        statusLabel?.text = text
    }

    fun setButtonStates(speaking: Boolean) {
        generateBtn?.isEnabled = !speaking
        generateBtn?.alpha = if (speaking) 0.4f else 1.0f
        stopBtn?.isEnabled = speaking
        stopBtn?.alpha = if (speaking) 1.0f else 0.4f
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
                onGenerate(text)
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
        ) { onStop() }
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
