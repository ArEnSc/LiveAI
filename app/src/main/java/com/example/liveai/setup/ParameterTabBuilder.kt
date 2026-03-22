package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R
import com.example.liveai.live2d.LAppLive2DManager

/**
 * Builds the Parameters tab content: searchable list + drill-down editor.
 */
class ParameterTabBuilder(
    private val context: Context,
    private val paramOverrides: MutableMap<String, Float>,
    private val glSurfaceView: () -> GLSurfaceView?,
    private val live2DManager: () -> LAppLive2DManager?
) {
    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val padV = (12 * dp).toInt()
    private val accentColor = ContextCompat.getColor(context, R.color.purple_200)
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)
    private val dividerColor = ContextCompat.getColor(context, R.color.panel_divider)

    /** The container view — add to content host. */
    var container: LinearLayout? = null; private set
    private var paramListView: LinearLayout? = null
    private var paramEditorView: LinearLayout? = null

    fun buildPlaceholder(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }
        val loadingLabel = TextView(context).apply {
            text = "Loading model parameters..."
            setTextColor(dimWhite)
            textSize = 13f
        }
        content.addView(loadingLabel)
        container = content
        return content
    }

    fun populate(params: List<LAppLive2DManager.ParameterInfo>) {
        val cont = container ?: return
        cont.removeAllViews()

        if (params.isEmpty()) {
            cont.addView(TextView(context).apply {
                text = "No parameters found"
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 13f
            })
            return
        }

        val listView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        paramListView = listView

        // Search field
        val searchField = EditText(context).apply {
            hint = "Search parameters..."
            setHintTextColor(dimWhite)
            setTextColor(textOnPanel)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.panel_background))
                cornerRadius = 8 * dp
                setStroke((1 * dp).toInt(), dividerColor)
            }
            setPadding(padH, (8 * dp).toInt(), padH, (8 * dp).toInt())
            isSingleLine = true
        }
        listView.addView(searchField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(padH, (8 * dp).toInt(), padH, (8 * dp).toInt())
        })

        // Editor view (hidden initially)
        val editorView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        paramEditorView = editorView

        val cellWrappers = mutableListOf<Pair<String, View>>()

        for (param in params) {
            val range = param.max - param.min
            if (range <= 0f) continue

            val hasOverride = paramOverrides.containsKey(param.id)
            val initialValue = if (hasOverride) {
                paramOverrides[param.id] ?: param.defaultValue
            } else {
                param.defaultValue
            }

            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, (12 * dp).toInt(), padH, (12 * dp).toInt())
                isClickable = true
                isFocusable = true
            }

            val nameLabel = TextView(context).apply {
                text = param.displayName
                setTextColor(if (hasOverride) textOnPanel else dimWhite)
                textSize = 13f
                setTypeface(null, if (hasOverride) Typeface.BOLD else Typeface.NORMAL)
            }
            cell.addView(nameLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val cellValue = TextView(context).apply {
                text = "%.2f".format(initialValue)
                setTextColor(if (hasOverride) accentColor else dimWhite)
                textSize = 13f
                gravity = Gravity.END
            }
            cell.addView(cellValue)

            val chevron = TextView(context).apply {
                text = "  \u203A"
                setTextColor(dimWhite)
                textSize = 16f
            }
            cell.addView(chevron)

            val divider = View(context).apply { setBackgroundColor(dividerColor) }
            val cellWrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(cell)
                addView(divider, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ))
            }

            cell.setOnClickListener {
                openEditor(param, initialValue, nameLabel, cellValue)
            }

            cellWrappers.add(param.displayName.lowercase() to cellWrapper)
            listView.addView(cellWrapper)
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.lowercase()?.trim() ?: ""
                for ((name, wrapper) in cellWrappers) {
                    wrapper.visibility = if (query.isEmpty() || name.contains(query)) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
            }
        })

        cont.addView(listView)
        cont.addView(editorView)
    }

    private fun openEditor(
        param: LAppLive2DManager.ParameterInfo,
        currentValue: Float,
        nameLabel: TextView,
        cellValue: TextView
    ) {
        val editor = paramEditorView ?: return
        val list = paramListView ?: return
        val range = param.max - param.min
        val steps = 200

        val valueBefore = paramOverrides[param.id] ?: param.defaultValue

        editor.removeAllViews()

        val title = TextView(context).apply {
            text = param.displayName
            setTextColor(textOnPanel)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(padH, (12 * dp).toInt(), padH, (4 * dp).toInt())
        }
        editor.addView(title)

        val valueDisplay = TextView(context).apply {
            text = "%.2f".format(currentValue)
            setTextColor(accentColor)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(padH, (4 * dp).toInt(), padH, (8 * dp).toInt())
        }
        editor.addView(valueDisplay)

        val sliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, 0, padH, (4 * dp).toInt())
        }

        val minLabel = TextView(context).apply {
            text = "%.1f".format(param.min)
            setTextColor(dimWhite)
            textSize = 11f
        }
        sliderRow.addView(minLabel)

        val slider = SeekBar(context).apply {
            max = steps
            progress = ((currentValue - param.min) / range * steps).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = param.min + (progress.toFloat() / steps) * range
                    valueDisplay.text = "%.2f".format(value)
                    paramOverrides[param.id] = value
                    glSurfaceView()?.queueEvent {
                        live2DManager()?.setParameterOverride(param.id, value)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        sliderRow.addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val maxLabel = TextView(context).apply {
            text = "%.1f".format(param.max)
            setTextColor(dimWhite)
            textSize = 11f
        }
        sliderRow.addView(maxLabel)
        editor.addView(sliderRow)

        editor.addView(View(context).apply { setBackgroundColor(dividerColor) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ).apply { topMargin = (8 * dp).toInt() })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, (10 * dp).toInt(), padH, (8 * dp).toInt())
        }

        fun closeEditor() {
            editor.animate()
                .translationY(-editor.height.toFloat())
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    editor.visibility = View.GONE
                    editor.translationY = 0f
                    editor.alpha = 1f
                    list.alpha = 0f
                    list.visibility = View.VISIBLE
                    list.animate().alpha(1f).setDuration(150).start()
                }
                .start()
        }

        val btnReset = SetupUiHelpers.makePillButton(context, "Reset", Color.TRANSPARENT, dimWhite, dp) {
            paramOverrides.remove(param.id)
            slider.progress = ((param.defaultValue - param.min) / range * steps).toInt()
            valueDisplay.text = "%.2f".format(param.defaultValue)
            nameLabel.setTextColor(dimWhite)
            nameLabel.setTypeface(null, Typeface.NORMAL)
            cellValue.text = "%.2f".format(param.defaultValue)
            cellValue.setTextColor(dimWhite)
            glSurfaceView()?.queueEvent {
                live2DManager()?.clearParameterOverride(param.id)
            }
            closeEditor()
        }
        btnRow.addView(btnReset, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) })

        val btnCancel = SetupUiHelpers.makePillButton(context, "Cancel", Color.TRANSPARENT, dimWhite, dp) {
            if (valueBefore == param.defaultValue && !paramOverrides.containsKey(param.id)) {
                glSurfaceView()?.queueEvent {
                    live2DManager()?.clearParameterOverride(param.id)
                }
            } else {
                paramOverrides[param.id] = valueBefore
                glSurfaceView()?.queueEvent {
                    live2DManager()?.setParameterOverride(param.id, valueBefore)
                }
            }
            cellValue.text = "%.2f".format(valueBefore)
            closeEditor()
        }
        btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) })

        val btnDone = SetupUiHelpers.makePillButton(context, "Done", accentColor, textOnPanel, dp) {
            val finalValue = paramOverrides[param.id]
            if (finalValue != null) {
                cellValue.text = "%.2f".format(finalValue)
                cellValue.setTextColor(accentColor)
                nameLabel.setTextColor(textOnPanel)
                nameLabel.setTypeface(null, Typeface.BOLD)
            }
            closeEditor()
        }
        btnRow.addView(btnDone, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) })

        editor.addView(btnRow)

        list.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                list.visibility = View.GONE
                list.alpha = 1f
                editor.translationY = -editor.height.toFloat().coerceAtLeast(200f)
                editor.alpha = 0f
                editor.visibility = View.VISIBLE
                editor.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .start()
            }
            .start()
    }
}
