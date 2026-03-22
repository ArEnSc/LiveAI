package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R

/** Shared UI factory methods used by all tab builders. */
object SetupUiHelpers {

    fun makePillButton(
        context: Context,
        label: String,
        fillColor: Int,
        textColor: Int,
        dp: Float,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            setTextColor(textColor)
            textSize = 13f
            isAllCaps = false
            val shape = GradientDrawable().apply {
                cornerRadius = 24 * dp
                setColor(fillColor)
                if (fillColor == Color.TRANSPARENT) {
                    setStroke((1.5f * dp).toInt(), ContextCompat.getColor(context, R.color.panel_btn_outline))
                }
            }
            background = shape
            setPadding((16 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (6 * dp).toInt())
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
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
}
