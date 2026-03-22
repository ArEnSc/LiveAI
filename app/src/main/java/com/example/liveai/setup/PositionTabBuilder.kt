package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.example.liveai.R

/**
 * Builds the Position & Scale tab content.
 */
class PositionTabBuilder(
    private val context: Context,
    private val onScaleChanged: (Float) -> Unit,
    private val onCenterHorizontal: () -> Unit,
    private val onCenterVertical: () -> Unit,
    private val initialScale: Float
) {
    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val padV = (12 * dp).toInt()
    private val accentColor = ContextCompat.getColor(context, R.color.purple_200)
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)

    /** The scale slider row — kept so Reset All can update it externally. */
    var scaleSlider: LinearLayout? = null
        private set

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
        }

        val slider = SetupUiHelpers.makeSliderRow(
            context = context,
            label = "Scale",
            initialValue = initialScale,
            formatValue = { "%.1fx".format(it) },
            min = 0.5f,
            max = 10.0f,
            steps = 950,
            dp = dp,
            dimWhite = dimWhite,
            textOnPanel = textOnPanel
        ) { value, _ ->
            onScaleChanged(value)
        }
        scaleSlider = slider
        content.addView(slider)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
        }

        val btnCenterH = SetupUiHelpers.makePillButton(context, "Center Horizontal", Color.TRANSPARENT, textOnPanel, dp) {
            onCenterHorizontal()
        }
        btnRow.addView(btnCenterH, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, (8 * dp).toInt(), 0) })

        val btnCenterV = SetupUiHelpers.makePillButton(context, "Center Vertical", Color.TRANSPARENT, textOnPanel, dp) {
            onCenterVertical()
        }
        btnRow.addView(btnCenterV)
        content.addView(btnRow)

        return content
    }
}
