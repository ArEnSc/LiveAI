package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout

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
    private val t = PanelTheme.from(context)

    /** The scale slider row — kept so Reset All can update it externally. */
    var scaleSlider: LinearLayout? = null
        private set

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(t.padH, t.padV, t.padH, t.padV)
        }

        val slider = SetupUiHelpers.makeSliderRow(
            context = context,
            label = "Scale",
            initialValue = initialScale,
            formatValue = { "%.1fx".format(it) },
            min = 0.5f,
            max = 10.0f,
            steps = 950,
            dp = t.dp,
            dimWhite = t.dimWhite,
            textOnPanel = t.textOnPanel
        ) { value, _ ->
            onScaleChanged(value)
        }
        scaleSlider = slider
        content.addView(slider)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * t.dp).toInt(), 0, (4 * t.dp).toInt())
        }

        val btnCenterH = SetupUiHelpers.makePillButton(context, "Center Horizontal", Color.TRANSPARENT, t.textOnPanel, t.dp) {
            onCenterHorizontal()
        }
        btnRow.addView(btnCenterH, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, (8 * t.dp).toInt(), 0) })

        val btnCenterV = SetupUiHelpers.makePillButton(context, "Center Vertical", Color.TRANSPARENT, t.textOnPanel, t.dp) {
            onCenterVertical()
        }
        btnRow.addView(btnCenterV)
        content.addView(btnRow)

        return content
    }
}
