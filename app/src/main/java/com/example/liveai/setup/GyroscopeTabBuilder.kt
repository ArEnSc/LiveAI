package com.example.liveai.setup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R
import com.example.liveai.gyroscope.GyroAxis
import com.example.liveai.gyroscope.GyroBinding
import com.example.liveai.gyroscope.GyroMotionConfig
import com.example.liveai.live2d.LAppLive2DManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds the Gyroscope tab content with a drill-down UI matching
 * the interaction zone editor pattern:
 *
 *   Binding list  →  Parameter picker (search)
 *                 →  Binding editor (axis, invert, scale)
 */
class GyroscopeTabBuilder(
    private val context: Context,
    initialConfig: GyroMotionConfig,
    private val onConfigChanged: (GyroMotionConfig) -> Unit
) {
    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val accentColor = ContextCompat.getColor(context, R.color.purple_200)
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)
    private val dividerColor = ContextCompat.getColor(context, R.color.panel_divider)
    private val panelBg = ContextCompat.getColor(context, R.color.panel_background)
    private val dangerColor = 0xFFCC4444.toInt()

    private var enabled = initialConfig.enabled
    private val bindings = initialConfig.bindings.toMutableList()

    /** The root container — set once in [build]. All navigation replaces its children. */
    private var root: LinearLayout? = null

    /** Editing state for binding editor drill-down. */
    private var editingBindingIndex = -1

    /** Available model params — populated after model loads. */
    private var availableParams: List<LAppLive2DManager.ParameterInfo> = emptyList()

    fun setAvailableParams(params: List<LAppLive2DManager.ParameterInfo>) {
        availableParams = params
    }

    private fun notifyChanged() {
        onConfigChanged(GyroMotionConfig(enabled = enabled, bindings = bindings.toList()))
    }

    // ── Build ────────────────────────────────────────────────────────────

    fun build(): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, (12 * dp).toInt(), padH, (12 * dp).toInt())
            visibility = View.GONE
        }
        root = content
        showBindingList()
        return content
    }

    // ── Binding List (main view) ─────────────────────────────────────────

    private fun showBindingList() {
        val container = root ?: return
        container.removeAllViews()

        // Global enable toggle
        val enableRow = makeRow()
        val enableSwitch = Switch(context).apply {
            text = "Tilt Motion"
            setTextColor(dimWhite)
            textSize = 13f
            isChecked = enabled
            setOnCheckedChangeListener { _, checked ->
                enabled = checked
                notifyChanged()
            }
        }
        enableRow.addView(enableSwitch, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        enableRow.addView(makeHint("Tilt phone to move"))
        container.addView(enableRow)

        container.addView(makeDivider())

        // Section header
        container.addView(makeLabel("Parameter Bindings"))
        container.addView(makeHint("Each binding maps a tilt axis to a model parameter."))

        // Binding rows
        if (bindings.isEmpty()) {
            container.addView(makeHint("No bindings — tap + Add Parameter"))
        } else {
            for ((bi, binding) in bindings.withIndex()) {
                container.addView(buildBindingRow(binding, bi))
            }
        }

        // Add button
        container.addView(makePillButton("+ Add Parameter", Color.TRANSPARENT, textOnPanel) {
            showParameterPicker()
        }, centredWrap(topMargin = 8))
    }

    private fun buildBindingRow(binding: GyroBinding, bindingIndex: Int): View {
        val row = makeRow(vPad = 6)

        // Axis indicator (tap to toggle)
        val axisSymbol = if (binding.axis == GyroAxis.TILT_X) "\u2194" else "\u2195"
        val axisBtn = TextView(context).apply {
            text = axisSymbol
            textSize = 20f; setTextColor(accentColor)
            setPadding((4 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            isClickable = true
            setOnClickListener {
                val newAxis = if (bindings[bindingIndex].axis == GyroAxis.TILT_X)
                    GyroAxis.TILT_Y else GyroAxis.TILT_X
                bindings[bindingIndex] = bindings[bindingIndex].copy(axis = newAxis)
                text = if (newAxis == GyroAxis.TILT_X) "\u2194" else "\u2195"
                notifyChanged()
            }
        }
        row.addView(axisBtn)

        // Name + scale info (tap to edit)
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                editingBindingIndex = bindingIndex
                showBindingEditor()
            }
        }
        textCol.addView(makeLabel(binding.displayName, size = 13f, bold = false, topPad = 0, bottomPad = 0))
        val inverted = if (binding.scale < 0f) " (inv)" else ""
        textCol.addView(makeHint("%.0f°%s  •  %s".format(
            abs(binding.scale),
            inverted,
            if (binding.axis == GyroAxis.TILT_X) "Roll" else "Pitch"
        )))
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Remove button
        row.addView(TextView(context).apply {
            text = "\u2715"; textSize = 16f; setTextColor(dangerColor)
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            setOnClickListener {
                bindings.removeAt(bindingIndex)
                notifyChanged()
                showBindingList()
            }
        })

        return wrapWithDivider(row)
    }

    // ── Parameter Picker ─────────────────────────────────────────────────

    private fun showParameterPicker() {
        val container = root ?: return
        container.removeAllViews()

        container.addView(makeTextButton("\u2190 Cancel") { showBindingList() })
        container.addView(makeLabel("Choose a parameter", size = 14f))

        val searchField = makeRoundedEditText("").apply {
            hint = "Search..."
            setHintTextColor(dimWhite)
            textSize = 13f
        }
        container.addView(searchField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, (8 * dp).toInt()) })

        val existingIds = bindings.map { it.paramId }.toSet()
        val cellWrappers = mutableListOf<Pair<String, View>>()

        if (availableParams.isNotEmpty()) {
            for (param in availableParams) {
                if (param.max - param.min <= 0f) continue
                if (isPhysicsOutputParam(param.id)) continue
                val alreadyAdded = param.id in existingIds
                val cell = buildParameterCell(param.id, param.displayName, alreadyAdded)
                cellWrappers.add(param.displayName.lowercase() to cell)
                container.addView(cell)
            }
        } else {
            // Fallback: common params for when model hasn't loaded yet
            val fallback = listOf("ParamAngleX", "ParamAngleY", "ParamAngleZ",
                "ParamBodyAngleX", "ParamBodyAngleY", "ParamBodyAngleZ",
                "ParamEyeBallX", "ParamEyeBallY")
            for (id in fallback) {
                val alreadyAdded = id in existingIds
                val cell = buildParameterCell(id, id, alreadyAdded)
                cellWrappers.add(id.lowercase() to cell)
                container.addView(cell)
            }
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.lowercase()?.trim() ?: ""
                for ((name, wrapper) in cellWrappers) {
                    wrapper.visibility = if (q.isEmpty() || name.contains(q))
                        View.VISIBLE else View.GONE
                }
            }
        })
    }

    private fun buildParameterCell(
        paramId: String,
        displayName: String,
        alreadyAdded: Boolean
    ): View {
        val cell = makeRow(hPad = padH, vPad = 10).apply {
            isClickable = !alreadyAdded
            isFocusable = !alreadyAdded
            if (!alreadyAdded) {
                setOnClickListener {
                    addBindingForParam(paramId, displayName)
                    showBindingList()
                }
            }
        }

        cell.addView(TextView(context).apply {
            text = displayName
            setTextColor(if (alreadyAdded) dimWhite else textOnPanel)
            textSize = 13f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (alreadyAdded) {
            cell.addView(makeHint("added"))
        }

        return wrapWithDivider(cell)
    }

    private fun addBindingForParam(paramId: String, displayName: String) {
        // Smart defaults: Y params get TILT_Y + inverted, others get TILT_X
        val isYParam = paramId.contains("AngleY", ignoreCase = true) ||
            paramId.contains("EyeBallY", ignoreCase = true)
        val axis = if (isYParam) GyroAxis.TILT_Y else GyroAxis.TILT_X
        val scale = if (isYParam) -15f else 15f

        bindings.add(GyroBinding(
            paramId = paramId,
            displayName = displayName,
            axis = axis,
            scale = scale
        ))
        notifyChanged()
    }

    // ── Binding Editor ───────────────────────────────────────────────────

    private fun showBindingEditor() {
        val container = root ?: return
        val binding = bindings[editingBindingIndex]

        container.removeAllViews()

        container.addView(makeTextButton("\u2190 Back") { showBindingList() })
        container.addView(makeLabel(binding.displayName, size = 15f, bottomPad = 12))

        // Axis toggle
        container.addView(buildAxisToggle(binding))

        // Invert toggle
        container.addView(buildInvertToggle(binding))

        // Scale slider
        container.addView(buildScaleSlider(binding))
    }

    private fun buildAxisToggle(binding: GyroBinding): View {
        val row = makeRow(bottomPad = 12)
        row.addView(makeLabel("Tilt axis: ", size = 13f, bold = false, topPad = 0, bottomPad = 0))

        val btnX = makePillButton("\u2194 Roll (X)",
            if (binding.axis == GyroAxis.TILT_X) accentColor else Color.TRANSPARENT, textOnPanel) {}
        val btnY = makePillButton("\u2195 Pitch (Y)",
            if (binding.axis == GyroAxis.TILT_Y) accentColor else Color.TRANSPARENT, textOnPanel) {}

        fun select(axis: GyroAxis) {
            (btnX.background as? GradientDrawable)
                ?.setColor(if (axis == GyroAxis.TILT_X) accentColor else Color.TRANSPARENT)
            (btnY.background as? GradientDrawable)
                ?.setColor(if (axis == GyroAxis.TILT_Y) accentColor else Color.TRANSPARENT)
            bindings[editingBindingIndex] = bindings[editingBindingIndex].copy(axis = axis)
            notifyChanged()
        }
        btnX.setOnClickListener { select(GyroAxis.TILT_X) }
        btnY.setOnClickListener { select(GyroAxis.TILT_Y) }

        row.addView(btnX, wrapWithMarginEnd(6))
        row.addView(btnY)
        return row
    }

    private fun buildInvertToggle(binding: GyroBinding): View {
        val isInverted = binding.scale < 0f
        val row = makeRow(bottomPad = 8)
        row.addView(makeLabel("Inverted: ", size = 13f, bold = false, topPad = 0, bottomPad = 0))

        val btn = makePillButton(
            if (isInverted) "ON" else "OFF",
            if (isInverted) accentColor else Color.TRANSPARENT,
            textOnPanel
        ) {}
        btn.setOnClickListener {
            bindings[editingBindingIndex] = bindings[editingBindingIndex].copy(
                scale = -bindings[editingBindingIndex].scale
            )
            notifyChanged()
            showBindingEditor()
        }
        row.addView(btn)
        return row
    }

    private fun buildScaleSlider(binding: GyroBinding): View {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val absScale = abs(binding.scale).roundToInt().coerceIn(1, 30)
        val label = TextView(context).apply {
            text = "Scale: ${absScale}°"
            setTextColor(accentColor); textSize = 15f
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        layout.addView(label)
        layout.addView(makeHint("Maximum angle the parameter moves when fully tilted."))
        layout.addView(makeSeekBar(max = 30, progress = absScale) { progress ->
            val value = progress.coerceAtLeast(1)
            label.text = "Scale: ${value}°"
            val sign = if (bindings[editingBindingIndex].scale < 0f) -1f else 1f
            bindings[editingBindingIndex] = bindings[editingBindingIndex].copy(
                scale = value.toFloat() * sign
            )
            notifyChanged()
        })
        return layout
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Heuristic: physics-output params (hair, bust, skirt, sleeve, ribbon, etc.)
     * are driven by the physics simulation — binding gyro to them would fight
     * the physics engine and produce jittery results.
     */
    private fun isPhysicsOutputParam(paramId: String): Boolean {
        val id = paramId.lowercase()
        return id.contains("hair") || id.contains("bust") || id.contains("skirt") ||
            id.contains("sleeve") || id.contains("ribbon") || id.contains("cloth") ||
            id.contains("cape") || id.contains("tail") || id.contains("ear") ||
            id.contains("ahoge") || id.contains("kemono")
    }

    private fun makeRow(
        hPad: Int = 0,
        vPad: Int = 0,
        topPad: Int = vPad,
        bottomPad: Int = vPad
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(hPad, (topPad * dp).toInt(), hPad, (bottomPad * dp).toInt())
    }

    private fun makeLabel(
        text: String,
        size: Float = 13f,
        bold: Boolean = true,
        topPad: Int = 8,
        bottomPad: Int = 4
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(textOnPanel)
        textSize = size
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, (topPad * dp).toInt(), 0, (bottomPad * dp).toInt())
    }

    private fun makeHint(
        text: String,
        topPad: Int = 0,
        bottomPad: Int = 4
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(dimWhite)
        textSize = 11f
        setPadding(0, (topPad * dp).toInt(), 0, (bottomPad * dp).toInt())
    }

    private fun makeTextButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label; setTextColor(accentColor); textSize = 14f
        setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun makeRoundedEditText(initialText: String): EditText = EditText(context).apply {
        setText(initialText)
        setTextColor(textOnPanel)
        textSize = 13f
        background = GradientDrawable().apply {
            setColor(panelBg)
            cornerRadius = 8 * dp
            setStroke((1 * dp).toInt(), dividerColor)
        }
        setPadding(padH, (8 * dp).toInt(), padH, (8 * dp).toInt())
        isSingleLine = true
    }

    private fun makeSeekBar(max: Int, progress: Int, onChanged: (Int) -> Unit): SeekBar =
        SeekBar(context).apply {
            this.max = max
            this.progress = progress
            setPadding(padH, (4 * dp).toInt(), padH, (12 * dp).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) onChanged(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

    private fun makePillButton(
        label: String, fillColor: Int, textColor: Int, onClick: () -> Unit
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

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(dividerColor)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ).apply { setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) }
    }

    private fun wrapWithDivider(view: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(view)
        addView(View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()))
    }

    private fun wrapWithMarginEnd(marginDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 0, (marginDp * dp).toInt(), 0) }

    private fun centredWrap(topMargin: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setMargins(0, (topMargin * dp).toInt(), 0, 0)
    }
}
