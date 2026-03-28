package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R

/**
 * Shared styling constants resolved from the app theme.
 * Create once per Context via [PanelTheme.from] and pass to UI builders.
 */
data class PanelTheme(
    val dp: Float,
    val padH: Int,
    val padV: Int,
    val accentColor: Int,
    val textOnPanel: Int,
    val dimWhite: Int,
    val hintColor: Int,
    val dividerColor: Int,
    val panelBg: Int,
    val dangerColor: Int
) {
    companion object {
        fun from(context: Context): PanelTheme {
            val dp = context.resources.displayMetrics.density
            return PanelTheme(
                dp = dp,
                padH = (16 * dp).toInt(),
                padV = (12 * dp).toInt(),
                accentColor = ContextCompat.getColor(context, R.color.purple_200),
                textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel),
                dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim),
                hintColor = ContextCompat.getColor(context, R.color.text_on_panel_hint),
                dividerColor = ContextCompat.getColor(context, R.color.panel_divider),
                panelBg = ContextCompat.getColor(context, R.color.panel_background),
                dangerColor = 0xFFCC4444.toInt()
            )
        }
    }
}

/** Shared UI factory methods used by all tab builders. */
object SetupUiHelpers {

    fun makePillButton(
        context: Context,
        label: String,
        fillColor: Int,
        textColor: Int,
        dp: Float,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = label; setTextColor(textColor); textSize = 13f; isAllCaps = false
        val outlineColor = ContextCompat.getColor(context, R.color.panel_btn_outline)
        background = GradientDrawable().apply {
            cornerRadius = 24 * dp; setColor(fillColor)
            if (fillColor == Color.TRANSPARENT) setStroke((1.5f * dp).toInt(), outlineColor)
        }
        setPadding((16 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (6 * dp).toInt())
        minHeight = 0; minimumHeight = 0; stateListAnimator = null
        setOnClickListener { onClick() }
    }

    fun makeSliderRow(
        context: Context,
        label: String,
        initialValue: Float,
        formatValue: (Float) -> String,
        min: Float,
        max: Float,
        steps: Int,
        dp: Float,
        dimWhite: Int,
        textOnPanel: Int,
        onChange: (Float, TextView) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }
        val lbl = TextView(context).apply {
            text = label
            setTextColor(dimWhite)
            textSize = 13f
        }
        row.addView(lbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f))

        val valueLabel = TextView(context).apply {
            text = formatValue(initialValue)
            setTextColor(textOnPanel)
            textSize = 13f
            gravity = Gravity.END
            minWidth = (36 * dp).toInt()
        }

        val slider = SeekBar(context).apply {
            this.max = steps
            progress = ((initialValue - min) / (max - min) * steps).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + (progress.toFloat() / steps) * (max - min)
                    valueLabel.text = formatValue(value)
                    onChange(value, valueLabel)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        row.addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))
        row.addView(valueLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f))
        return row
    }

    fun makeRow(
        context: Context,
        dp: Float,
        hPad: Int = 0,
        vPad: Int = 0,
        topPad: Int = vPad,
        bottomPad: Int = vPad
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(hPad, (topPad * dp).toInt(), hPad, (bottomPad * dp).toInt())
    }

    fun makeLabel(
        context: Context,
        text: String,
        textColor: Int,
        dp: Float,
        size: Float = 13f,
        bold: Boolean = true,
        topPad: Int = 8,
        bottomPad: Int = 4
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(textColor)
        textSize = size
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, (topPad * dp).toInt(), 0, (bottomPad * dp).toInt())
    }

    fun makeHint(
        context: Context,
        text: String,
        hintColor: Int,
        dp: Float,
        maxLines: Int = Int.MAX_VALUE,
        topPad: Int = 0,
        bottomPad: Int = 4
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(hintColor)
        textSize = 11f
        this.maxLines = maxLines
        setPadding(0, (topPad * dp).toInt(), 0, (bottomPad * dp).toInt())
    }

    fun makeDivider(
        context: Context,
        dividerColor: Int,
        dp: Float,
        topMargin: Int = (8 * dp).toInt(),
        bottomMargin: Int = (8 * dp).toInt()
    ): View = View(context).apply {
        setBackgroundColor(dividerColor)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ).apply { setMargins(0, topMargin, 0, bottomMargin) }
    }

    fun wrapWithDivider(
        context: Context,
        view: View,
        dividerColor: Int,
        dp: Float
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(view)
        addView(View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()))
    }

    fun makeTextButton(
        context: Context,
        label: String,
        accentColor: Int,
        dp: Float,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label; setTextColor(accentColor); textSize = 14f
        setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }
}
