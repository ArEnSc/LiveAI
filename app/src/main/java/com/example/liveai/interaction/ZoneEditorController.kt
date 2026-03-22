package com.example.liveai.interaction

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.liveai.R
import com.example.liveai.live2d.LAppLive2DManager
import java.util.UUID

/**
 * Builds and manages the zone list / zone editor / binding editor drill-down UI.
 *
 * Constructed by WallpaperSetupActivity and placed in the Interaction tab container.
 * Delegates zone overlay positioning to callbacks on the host activity.
 */
class ZoneEditorController(
    private val context: Context,
    private val container: LinearLayout,
    private val managerProvider: () -> LAppLive2DManager?,
    private val overlayHost: FrameLayout?,
    private val onPanelVisibility: (visible: Boolean) -> Unit,
    private val onZonesSaved: (List<InteractionZone>) -> Unit
) {
    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val accentColor = ContextCompat.getColor(context, R.color.purple_200)
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)
    private val dividerColor = ContextCompat.getColor(context, R.color.panel_divider)
    private val panelBg = ContextCompat.getColor(context, R.color.panel_background)

    private var zones = ZoneRepository.loadZones(context).toMutableList()

    // Navigation state
    private var zoneListView: LinearLayout? = null
    private var zoneDetailView: LinearLayout? = null
    private var bindingEditorView: LinearLayout? = null
    private var editingZoneIndex = -1
    private var editingBindingIndex = -1

    // Active overlay
    private var activeOverlay: HitZoneOverlayView? = null
    private var overlayDoneBtn: View? = null

    /** Build and show the zone list in the container. */
    fun buildZoneList() {
        container.removeAllViews()

        val listView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        zoneListView = listView

        val label = TextView(context).apply {
            text = "Interaction zones"
            setTextColor(textOnPanel)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        listView.addView(label)

        val hint = TextView(context).apply {
            text = "Each zone maps a touch region to model parameters."
            setTextColor(dimWhite)
            textSize = 12f
            setPadding(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
        }
        listView.addView(hint)

        for ((index, zone) in zones.withIndex()) {
            listView.addView(buildZoneCard(zone, index))
        }

        val addBtn = makePillButton("+ Add Zone", Color.TRANSPARENT, textOnPanel) {
            addNewZone()
        }
        listView.addView(addBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = (12 * dp).toInt()
        })

        container.addView(listView)
    }

    private fun buildZoneCard(zone: InteractionZone, index: Int): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, (12 * dp).toInt(), padH, (12 * dp).toInt())
            isClickable = true
            isFocusable = true
        }

        // Color dot
        val dot = View(context).apply {
            val size = (12 * dp).toInt()
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(zone.color)
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, (10 * dp).toInt(), 0)
            }
        }
        card.addView(dot)

        // Name + binding summary
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val nameLabel = TextView(context).apply {
            text = zone.name
            setTextColor(textOnPanel)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        textCol.addView(nameLabel)

        val bindingSummary = zone.bindings.joinToString(", ") { it.displayName }
        if (bindingSummary.isNotEmpty()) {
            val summaryLabel = TextView(context).apply {
                text = bindingSummary
                setTextColor(dimWhite)
                textSize = 11f
                maxLines = 1
            }
            textCol.addView(summaryLabel)
        }
        card.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Chevron
        val chevron = TextView(context).apply {
            text = "\u203A"
            setTextColor(dimWhite)
            textSize = 18f
        }
        card.addView(chevron)

        card.setOnClickListener { openZoneDetail(index) }

        // Wrap with divider
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
            addView(View(context).apply { setBackgroundColor(dividerColor) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()))
        }
        return wrapper
    }

    // --- Zone Detail ---

    private fun openZoneDetail(index: Int) {
        editingZoneIndex = index
        val zone = zones[index]

        container.removeAllViews()

        val detail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        zoneDetailView = detail

        // Back button
        val backBtn = makeTextButton("\u2190 Back") {
            dismissOverlay()
            saveZones()
            buildZoneList()
        }
        detail.addView(backBtn)

        // Zone name
        val nameField = EditText(context).apply {
            setText(zone.name)
            setTextColor(textOnPanel)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(panelBg)
                cornerRadius = 8 * dp
                setStroke((1 * dp).toInt(), dividerColor)
            }
            setPadding(padH, (8 * dp).toInt(), padH, (8 * dp).toInt())
            isSingleLine = true
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    zones[editingZoneIndex] = zones[editingZoneIndex].copy(name = text.toString().trim())
                }
            }
        }
        detail.addView(nameField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) })

        // Position rectangle button
        val posBtn = makePillButton("Position Rectangle", accentColor, textOnPanel) {
            showZoneOverlay(editingZoneIndex)
        }
        detail.addView(posBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
        })

        // Divider
        detail.addView(makeDivider())

        // Parameter bindings header
        val bindingsLabel = TextView(context).apply {
            text = "Parameter Bindings"
            setTextColor(textOnPanel)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
        }
        detail.addView(bindingsLabel)

        val bindingsHint = TextView(context).apply {
            text = "Drag inside this zone will drive these parameters."
            setTextColor(dimWhite)
            textSize = 11f
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        detail.addView(bindingsHint)

        // Binding rows
        for ((bi, binding) in zone.bindings.withIndex()) {
            detail.addView(buildBindingRow(binding, bi))
        }

        // Add binding button
        val addBindingBtn = makePillButton("+ Add Parameter", Color.TRANSPARENT, textOnPanel) {
            openParameterPicker()
        }
        detail.addView(addBindingBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = (8 * dp).toInt()
        })

        // Delete zone button
        detail.addView(makeDivider((16 * dp).toInt()))
        val deleteBtn = makePillButton("Delete Zone", 0xFFCC4444.toInt(), textOnPanel) {
            zones.removeAt(editingZoneIndex)
            dismissOverlay()
            saveZones()
            buildZoneList()
        }
        detail.addView(deleteBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        container.addView(detail)
    }

    private fun buildBindingRow(binding: ParameterBinding, bindingIndex: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }

        // Axis indicator
        val axisBtn = TextView(context).apply {
            text = if (binding.axis == DragAxis.HORIZONTAL) "\u2194" else "\u2195"
            textSize = 20f
            setTextColor(accentColor)
            setPadding((4 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            isClickable = true
            setOnClickListener {
                val zone = zones[editingZoneIndex]
                val newAxis = if (binding.axis == DragAxis.HORIZONTAL) DragAxis.VERTICAL else DragAxis.HORIZONTAL
                val updatedBinding = binding.copy(axis = newAxis)
                val newBindings = zone.bindings.toMutableList().apply { set(bindingIndex, updatedBinding) }
                zones[editingZoneIndex] = zone.copy(bindings = newBindings)
                text = if (newAxis == DragAxis.HORIZONTAL) "\u2194" else "\u2195"
            }
        }
        row.addView(axisBtn)

        // Name + strength
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val nameLabel = TextView(context).apply {
            text = binding.displayName
            setTextColor(textOnPanel)
            textSize = 13f
        }
        textCol.addView(nameLabel)

        val strengthLabel = TextView(context).apply {
            text = "${(binding.strength * 100).toInt()}%"
            setTextColor(dimWhite)
            textSize = 11f
        }
        textCol.addView(strengthLabel)

        // Tap name to open strength editor
        textCol.isClickable = true
        textCol.setOnClickListener {
            openBindingEditor(bindingIndex)
        }
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Remove button
        val removeBtn = TextView(context).apply {
            text = "\u2715"
            textSize = 16f
            setTextColor(0xFFCC4444.toInt())
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            setOnClickListener {
                val zone = zones[editingZoneIndex]
                val newBindings = zone.bindings.toMutableList().apply { removeAt(bindingIndex) }
                zones[editingZoneIndex] = zone.copy(bindings = newBindings)
                openZoneDetail(editingZoneIndex) // refresh
            }
        }
        row.addView(removeBtn)

        return row
    }

    // --- Binding Editor (strength slider) ---

    private fun openBindingEditor(bindingIndex: Int) {
        editingBindingIndex = bindingIndex
        val zone = zones[editingZoneIndex]
        val binding = zone.bindings[bindingIndex]

        container.removeAllViews()
        val editor = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        bindingEditorView = editor

        // Back
        val backBtn = makeTextButton("\u2190 Back") {
            openZoneDetail(editingZoneIndex)
        }
        editor.addView(backBtn)

        // Title
        val title = TextView(context).apply {
            text = binding.displayName
            setTextColor(textOnPanel)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, (8 * dp).toInt(), 0, (12 * dp).toInt())
        }
        editor.addView(title)

        // Axis toggle
        val axisRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        val axisLabel = TextView(context).apply {
            text = "Drag axis: "
            setTextColor(textOnPanel)
            textSize = 13f
        }
        axisRow.addView(axisLabel)

        val btnH = makePillButton(
            "\u2194 Horizontal",
            if (binding.axis == DragAxis.HORIZONTAL) accentColor else Color.TRANSPARENT,
            textOnPanel
        ) {}
        val btnV = makePillButton(
            "\u2195 Vertical",
            if (binding.axis == DragAxis.VERTICAL) accentColor else Color.TRANSPARENT,
            textOnPanel
        ) {}

        fun updateAxisButtons(axis: DragAxis) {
            val hBg = btnH.background as? GradientDrawable
            val vBg = btnV.background as? GradientDrawable
            if (axis == DragAxis.HORIZONTAL) {
                hBg?.setColor(accentColor); vBg?.setColor(Color.TRANSPARENT)
            } else {
                vBg?.setColor(accentColor); hBg?.setColor(Color.TRANSPARENT)
            }
            updateBinding { it.copy(axis = axis) }
        }

        btnH.setOnClickListener { updateAxisButtons(DragAxis.HORIZONTAL) }
        btnV.setOnClickListener { updateAxisButtons(DragAxis.VERTICAL) }

        axisRow.addView(btnH, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, (6 * dp).toInt(), 0) })
        axisRow.addView(btnV)
        editor.addView(axisRow)

        // Strength slider (-100..+100)
        val strengthValueLabel = TextView(context).apply {
            text = "Strength: ${(binding.strength * 100).toInt()}%"
            setTextColor(accentColor)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        editor.addView(strengthValueLabel)

        val strengthSlider = SeekBar(context).apply {
            max = 200 // maps to -100..+100
            progress = ((binding.strength + 1f) * 100f).toInt().coerceIn(0, 200)
            setPadding(padH, (4 * dp).toInt(), padH, (12 * dp).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val strength = (progress - 100) / 100f
                    strengthValueLabel.text = "Strength: ${(strength * 100).toInt()}%"
                    if (fromUser) {
                        updateBinding { it.copy(strength = strength) }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        editor.addView(strengthSlider)

        // Max value slider
        val maxValueLabel = TextView(context).apply {
            text = "Max value: ${binding.maxValue.toInt()}"
            setTextColor(textOnPanel)
            textSize = 13f
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        editor.addView(maxValueLabel)

        val maxSlider = SeekBar(context).apply {
            max = 60 // 0..60
            progress = binding.maxValue.toInt().coerceIn(0, 60)
            setPadding(padH, (4 * dp).toInt(), padH, (12 * dp).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val mv = progress.coerceAtLeast(1).toFloat()
                    maxValueLabel.text = "Max value: ${mv.toInt()}"
                    if (fromUser) {
                        updateBinding { it.copy(maxValue = mv) }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        editor.addView(maxSlider)

        container.addView(editor)
    }

    private fun updateBinding(transform: (ParameterBinding) -> ParameterBinding) {
        val zone = zones[editingZoneIndex]
        val binding = zone.bindings[editingBindingIndex]
        val updated = transform(binding)
        val newBindings = zone.bindings.toMutableList().apply { set(editingBindingIndex, updated) }
        zones[editingZoneIndex] = zone.copy(bindings = newBindings)
    }

    // --- Parameter Picker ---

    private fun openParameterPicker() {
        val params = managerProvider()?.parameterList ?: return

        container.removeAllViews()

        val picker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val backBtn = makeTextButton("\u2190 Cancel") {
            openZoneDetail(editingZoneIndex)
        }
        picker.addView(backBtn)

        val title = TextView(context).apply {
            text = "Choose a parameter"
            setTextColor(textOnPanel)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        }
        picker.addView(title)

        // Search
        val searchField = EditText(context).apply {
            hint = "Search..."
            setHintTextColor(dimWhite)
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
        picker.addView(searchField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, (8 * dp).toInt()) })

        val existingIds = zones[editingZoneIndex].bindings.map { it.paramId }.toSet()
        val cellWrappers = mutableListOf<Pair<String, View>>()

        for (param in params) {
            if (param.max - param.min <= 0f) continue

            val alreadyAdded = param.id in existingIds
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, (10 * dp).toInt(), padH, (10 * dp).toInt())
                isClickable = !alreadyAdded
                isFocusable = !alreadyAdded
                if (!alreadyAdded) {
                    setOnClickListener {
                        addBindingForParam(param)
                        openZoneDetail(editingZoneIndex)
                    }
                }
            }

            val nameLabel = TextView(context).apply {
                text = param.displayName
                setTextColor(if (alreadyAdded) dimWhite else textOnPanel)
                textSize = 13f
            }
            cell.addView(nameLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (alreadyAdded) {
                val addedTag = TextView(context).apply {
                    text = "added"
                    setTextColor(dimWhite)
                    textSize = 11f
                }
                cell.addView(addedTag)
            }

            val wrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(cell)
                addView(View(context).apply { setBackgroundColor(dividerColor) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()))
            }
            cellWrappers.add(param.displayName.lowercase() to wrapper)
            picker.addView(wrapper)
        }

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.lowercase()?.trim() ?: ""
                for ((name, wrapper) in cellWrappers) {
                    wrapper.visibility = if (q.isEmpty() || name.contains(q)) View.VISIBLE else View.GONE
                }
            }
        })

        container.addView(picker)
    }

    private fun addBindingForParam(param: LAppLive2DManager.ParameterInfo) {
        val zone = zones[editingZoneIndex]
        val isAngleParam = param.id.contains("Angle", ignoreCase = true)
        val defaultStrength = if (param.id.contains("AngleY") || param.id.contains("EyeBallY")) -1.0f else 1.0f
        val defaultMax = if (isAngleParam) 30f else (param.max - param.min) / 2f

        val newBinding = ParameterBinding(
            paramId = param.id,
            displayName = param.displayName,
            axis = DragAxis.HORIZONTAL,
            strength = defaultStrength,
            maxValue = defaultMax.coerceAtLeast(1f)
        )
        val newBindings = zone.bindings + newBinding
        zones[editingZoneIndex] = zone.copy(bindings = newBindings)
    }

    // --- Zone Overlay ---

    private fun showZoneOverlay(index: Int) {
        val host = overlayHost ?: return
        dismissOverlay()

        // Collapse the panel so the user sees the full model
        onPanelVisibility(false)

        val zone = zones[index]
        val overlay = HitZoneOverlayView(
            context = context,
            zoneNorm = zone.rect,
            zoneColor = zone.color,
            onZoneChanged = { newRect ->
                zones[index] = zones[index].copy(rect = newRect)
            }
        )
        activeOverlay = overlay

        host.addView(overlay, host.childCount - 1, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val doneBtn = makePillButton("Done Positioning", accentColor, textOnPanel) {
            dismissOverlay()
            saveZones()
        }
        doneBtn.tag = "zone_overlay_done_btn"
        overlayDoneBtn = doneBtn

        host.addView(doneBtn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (48 * dp).toInt()
        })
    }

    fun dismissOverlay() {
        val host = overlayHost ?: return
        val hadOverlay = activeOverlay != null
        activeOverlay?.let {
            host.removeView(it)
            activeOverlay = null
        }
        overlayDoneBtn?.let {
            host.removeView(it)
            overlayDoneBtn = null
        }
        // Restore the panel if we were showing an overlay
        if (hadOverlay) {
            onPanelVisibility(true)
        }
    }

    // --- Helpers ---

    private fun addNewZone() {
        val newZone = InteractionZone(
            id = UUID.randomUUID().toString(),
            name = "Zone ${zones.size + 1}",
            color = ZoneRepository.nextColor(zones),
            rect = RectF(0.30f, 0.40f, 0.70f, 0.60f),
            bindings = emptyList(),
            spring = ZoneRepository.defaultSpring()
        )
        zones.add(newZone)
        saveZones()
        openZoneDetail(zones.size - 1)
    }

    private fun saveZones() {
        ZoneRepository.saveZones(context, zones)
        onZonesSaved(zones)
    }

    private fun makePillButton(
        label: String,
        fillColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            setTextColor(textColor)
            textSize = 13f
            isAllCaps = false
            val outlineColor = ContextCompat.getColor(context, R.color.panel_btn_outline)
            val shape = GradientDrawable().apply {
                cornerRadius = 24 * dp
                setColor(fillColor)
                if (fillColor == Color.TRANSPARENT) {
                    setStroke((1.5f * dp).toInt(), outlineColor)
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

    private fun makeTextButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(accentColor)
            textSize = 14f
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun makeDivider(topMargin: Int = (8 * dp).toInt()): View {
        return View(context).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply { setMargins(0, topMargin, 0, (8 * dp).toInt()) }
        }
    }
}
