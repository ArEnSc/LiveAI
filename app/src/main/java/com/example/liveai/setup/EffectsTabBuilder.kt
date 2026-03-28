package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R
import com.example.liveai.live2d.PostProcessFilter

/**
 * Builds the Effects tab content (saturation, outline, color presets).
 */
class EffectsTabBuilder(
    private val context: Context,
    private val postProcess: PostProcessFilter
) {
    private val t = PanelTheme.from(context)

    /** Exposed so Reset All can update them. */
    var satSwitch: Switch? = null; private set
    var outSwitch: Switch? = null; private set
    var satSlider: LinearLayout? = null; private set
    var outSlider: LinearLayout? = null; private set

    private data class ColorPreset(val name: String, val color: Int, val r: Float, val g: Float, val b: Float)

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(t.padH, t.padV, t.padH, t.padV)
            visibility = View.GONE
        }

        // Saturation toggle + slider
        val satRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val satSw = Switch(context).apply {
            text = "Saturation"
            setTextColor(t.dimWhite)
            textSize = 13f
            isChecked = postProcess.isSaturationEnabled
            setOnCheckedChangeListener { _, checked ->
                postProcess.isSaturationEnabled = checked
            }
        }
        satSwitch = satSw
        satRow.addView(satSw, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(satRow)

        val satSl = SetupUiHelpers.makeSliderRow(
            context = context,
            label = "Amount",
            initialValue = postProcess.saturationAmount,
            formatValue = { "%.1f".format(it) },
            min = 0.0f,
            max = 3.0f,
            steps = 300,
            dp = t.dp,
            dimWhite = t.dimWhite,
            textOnPanel = t.textOnPanel
        ) { value, _ ->
            postProcess.saturationAmount = value
        }
        satSlider = satSl
        content.addView(satSl)

        // Spacer
        content.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (8 * t.dp).toInt()
        ))

        // Outline toggle + slider
        val outRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val outSw = Switch(context).apply {
            text = "Outline"
            setTextColor(t.dimWhite)
            textSize = 13f
            isChecked = postProcess.isOutlineEnabled
            setOnCheckedChangeListener { _, checked ->
                postProcess.isOutlineEnabled = checked
            }
        }
        outSwitch = outSw
        outRow.addView(outSw, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(outRow)

        val outSl = SetupUiHelpers.makeSliderRow(
            context = context,
            label = "Thickness",
            initialValue = postProcess.outlineThickness,
            formatValue = { "%d".format(it.toInt()) },
            min = 0.0f,
            max = 100.0f,
            steps = 100,
            dp = t.dp,
            dimWhite = t.dimWhite,
            textOnPanel = t.textOnPanel
        ) { value, _ ->
            postProcess.outlineThickness = value
        }
        outSlider = outSl
        content.addView(outSl)

        // Outline color row
        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * t.dp).toInt(), 0, (4 * t.dp).toInt())
        }
        val colorLabel = TextView(context).apply {
            text = "Color"
            setTextColor(t.dimWhite)
            textSize = 13f
            setPadding(0, 0, (12 * t.dp).toInt(), 0)
        }
        colorRow.addView(colorLabel)

        val presets = listOf(
            ColorPreset("Black", Color.BLACK, 0f, 0f, 0f),
            ColorPreset("White", Color.WHITE, 1f, 1f, 1f),
            ColorPreset("Red", Color.RED, 1f, 0f, 0f),
            ColorPreset("Blue", Color.BLUE, 0f, 0f, 1f),
            ColorPreset("Green", Color.rgb(0, 200, 0), 0f, 0.78f, 0f),
            ColorPreset("Gold", Color.rgb(255, 215, 0), 1f, 0.84f, 0f),
            ColorPreset("Pink", Color.rgb(255, 105, 180), 1f, 0.41f, 0.71f),
            ColorPreset("Cyan", Color.CYAN, 0f, 1f, 1f)
        )

        val colorScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val colorInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnSize = (28 * t.dp).toInt()
        val btnMargin = (5 * t.dp).toInt()
        for (preset in presets) {
            val btnShape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(preset.color)
                setStroke((1.5f * t.dp).toInt(), ContextCompat.getColor(context, R.color.panel_btn_outline))
            }
            val colorBtn = View(context).apply {
                background = btnShape
                setOnClickListener {
                    postProcess.setOutlineColor(preset.r, preset.g, preset.b, 1.0f)
                }
            }
            colorInner.addView(colorBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(btnMargin, 0, btnMargin, 0)
            })
        }
        colorScroll.addView(colorInner)
        colorRow.addView(colorScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(colorRow)

        return content
    }
}
