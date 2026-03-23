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
    private val onHintVisibility: (visible: Boolean) -> Unit,
    private val onZonesSaved: (List<InteractionZone>) -> Unit
) {
    // Model transform — updated by the host activity when scale/offset changes
    private var modelScale = 1f
    private var modelOffsetX = 0f
    private var modelOffsetY = 0f

    // Region visibility overlay
    private var regionOverlay: ZoneVisualOverlayView? = null
    private var regionsVisible = false

    // Styling constants
    private val dp = context.resources.displayMetrics.density
    private val padH = (16 * dp).toInt()
    private val accentColor = ContextCompat.getColor(context, R.color.purple_200)
    private val textOnPanel = ContextCompat.getColor(context, R.color.text_on_panel)
    private val dimWhite = ContextCompat.getColor(context, R.color.text_on_panel_dim)
    private val dividerColor = ContextCompat.getColor(context, R.color.panel_divider)
    private val panelBg = ContextCompat.getColor(context, R.color.panel_background)
    private val dangerColor = 0xFFCC4444.toInt()

    private var zones = ZoneRepository.loadZones(context).toMutableList()

    // Navigation state
    private var editingZoneIndex = -1
    private var editingBindingIndex = -1

    // Active overlay (zone position editor)
    private var activeOverlay: HitZoneOverlayView? = null
    private var overlayDoneBtn: View? = null
    private var overlayPreEditRect: RectF? = null

    // ── Public API ──────────────────────────────────────────────────────

    /** Called by the host activity when the model transform changes (drag/scale). */
    fun updateModelTransform(scale: Float, offsetX: Float, offsetY: Float) {
        modelScale = scale
        modelOffsetX = offsetX
        modelOffsetY = offsetY
        regionOverlay?.updateTransform(scale, offsetX, offsetY)
        activeOverlay?.updateTransform(scale, offsetX, offsetY)
    }

    /** Hide region overlay when leaving the interaction tab. */
    fun hideRegionsOnTabChange() {
        hideRegionOverlay()
        regionsVisible = false
    }

    fun dismissOverlay() {
        val host = overlayHost ?: return
        val hadOverlay = activeOverlay != null
        activeOverlay?.let { host.removeView(it) }
        activeOverlay = null
        overlayDoneBtn?.let { host.removeView(it) }
        overlayDoneBtn = null
        if (hadOverlay) {
            onPanelVisibility(true)
            onHintVisibility(true)
        }
    }

    // ── Zone List ───────────────────────────────────────────────────────

    /** Build and show the zone list in the container. */
    fun buildZoneList() {
        container.removeAllViews()

        val listView = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        listView.addView(makeLabel("Interaction zones", size = 14f))
        listView.addView(makeHint("Each zone maps a touch region to model parameters."))
        listView.addView(buildShowRegionsToggle())

        for ((index, zone) in zones.withIndex()) {
            listView.addView(buildZoneCard(zone, index))
        }

        listView.addView(buildZoneListButtons())
        container.addView(listView)
    }

    private fun buildShowRegionsToggle(): View {
        val row = makeRow(bottomPad = 12)
        row.addView(makeLabel("Show Regions", size = 13f, bold = false, topPad = 0, bottomPad = 0),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val toggleBtn = makePillButton(
            if (regionsVisible) "ON" else "OFF",
            if (regionsVisible) accentColor else Color.TRANSPARENT,
            textOnPanel
        ) {}
        toggleBtn.setOnClickListener {
            regionsVisible = !regionsVisible
            toggleBtn.text = if (regionsVisible) "ON" else "OFF"
            (toggleBtn.background as? GradientDrawable)
                ?.setColor(if (regionsVisible) accentColor else Color.TRANSPARENT)
            if (regionsVisible) showRegionOverlay() else hideRegionOverlay()
        }
        row.addView(toggleBtn)
        return row
    }

    private fun buildZoneListButtons(): View {
        val row = makeRow(topPad = 12)
        row.addView(makePillButton("+ Add Zone", Color.TRANSPARENT, textOnPanel) { addNewZone() },
            wrapWithMarginEnd(8))
        row.addView(makePillButton("Reset Zones", dangerColor, textOnPanel) {
            zones.clear()
            zones.addAll(ZoneRepository.createDefaultZones())
            saveZones()
            buildZoneList()
        })
        return row
    }

    private fun buildZoneCard(zone: InteractionZone, index: Int): View {
        val card = makeRow(hPad = padH, vPad = 12).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { openZoneDetail(index) }
        }

        // Color dot
        val dotSize = (12 * dp).toInt()
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(zone.color)
            }
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                setMargins(0, 0, (10 * dp).toInt(), 0)
            }
        }
        card.addView(dot)

        // Name + binding summary
        val textCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(makeLabel(zone.name, size = 14f, topPad = 0, bottomPad = 0))
        val summary = zone.bindings.joinToString(", ") { it.displayName }
        if (summary.isNotEmpty()) {
            textCol.addView(makeHint(summary, maxLines = 1, topPad = 0, bottomPad = 0))
        }
        card.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Chevron
        card.addView(TextView(context).apply {
            text = "\u203A"; setTextColor(dimWhite); textSize = 18f
        })

        return wrapWithDivider(card)
    }

    // ── Zone Detail ─────────────────────────────────────────────────────

    private fun openZoneDetail(index: Int) {
        editingZoneIndex = index
        val zone = zones[index]

        container.removeAllViews()
        val detail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        detail.addView(makeTextButton("\u2190 Back") {
            dismissOverlay(); saveZones(); buildZoneList()
        })
        detail.addView(buildZoneNameField(zone))
        detail.addView(makePillButton("Position Rectangle", accentColor, textOnPanel) {
            showZoneOverlay(editingZoneIndex)
        }, centredWrap(topMargin = 4, bottomMargin = 12))
        detail.addView(buildSensitivitySection(zone))
        detail.addView(makeDivider())
        detail.addView(buildBindingsSection(zone))
        detail.addView(makeDivider(topMargin = 16))
        if (zone.core) {
            detail.addView(makePillButton("Reset to Default", Color.TRANSPARENT, textOnPanel) {
                val defaults = ZoneRepository.createDefaultZones()
                val defaultZone = defaults.firstOrNull { it.name == zone.name }
                if (defaultZone != null) {
                    zones[editingZoneIndex] = defaultZone.copy(id = zone.id)
                    dismissOverlay(); saveZones(); openZoneDetail(editingZoneIndex)
                }
            }, centredWrap())
        } else {
            detail.addView(makePillButton("Delete Zone", dangerColor, textOnPanel) {
                zones.removeAt(editingZoneIndex); dismissOverlay(); saveZones(); buildZoneList()
            }, centredWrap())
        }

        container.addView(detail)
    }

    private fun buildZoneNameField(zone: InteractionZone): View {
        return makeRoundedEditText(zone.name).apply {
            setTypeface(null, Typeface.BOLD)
            textSize = 15f
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) updateZone { it.copy(name = text.toString().trim()) }
            }
        }.let { field ->
            // Wrap in a layout with margins
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(field, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) })
            }
        }
    }

    private fun buildSensitivitySection(zone: InteractionZone): View {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val percent = (zone.sensitivity * 10000f).toInt()
        val label = makeLabel("Sensitivity: $percent%", size = 13f, bold = false)
        layout.addView(label)
        layout.addView(makeHint("How far you need to drag before the parameter reaches full deflection."))
        layout.addView(makeSeekBar(max = 500, progress = percent.coerceIn(10, 500)) { progress ->
            val clamped = progress.coerceAtLeast(10)
            label.text = "Sensitivity: $clamped%"
            updateZone { it.copy(sensitivity = clamped / 10000f) }
        })
        return layout
    }

    private fun buildBindingsSection(zone: InteractionZone): View {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(makeLabel("Parameter Bindings"))
        layout.addView(makeHint("Drag inside this zone will drive these parameters."))

        for ((bi, binding) in zone.bindings.withIndex()) {
            layout.addView(buildBindingRow(binding, bi))
        }

        layout.addView(makePillButton("+ Add Parameter", Color.TRANSPARENT, textOnPanel) {
            openParameterPicker()
        }, centredWrap(topMargin = 8))

        return layout
    }

    private fun buildBindingRow(binding: ParameterBinding, bindingIndex: Int): View {
        val row = makeRow(vPad = 6)

        // Axis toggle
        val axisBtn = TextView(context).apply {
            text = binding.axis.symbol
            textSize = 20f; setTextColor(accentColor)
            setPadding((4 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            isClickable = true
            setOnClickListener {
                val newAxis = binding.axis.toggled
                updateZoneBinding(bindingIndex) { it.copy(axis = newAxis) }
                text = newAxis.symbol
            }
        }
        row.addView(axisBtn)

        // Name + strength (tap to edit)
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnClickListener { openBindingEditor(bindingIndex) }
        }
        textCol.addView(makeLabel(binding.displayName, size = 13f, bold = false, topPad = 0, bottomPad = 0))
        textCol.addView(makeHint("${(binding.strength * 100).toInt()}%", topPad = 0, bottomPad = 0))
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Remove button
        row.addView(TextView(context).apply {
            text = "\u2715"; textSize = 16f; setTextColor(dangerColor)
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            setOnClickListener {
                updateZone { z ->
                    z.copy(bindings = z.bindings.toMutableList().apply { removeAt(bindingIndex) })
                }
                openZoneDetail(editingZoneIndex)
            }
        })

        return row
    }

    // ── Binding Editor ──────────────────────────────────────────────────

    private fun openBindingEditor(bindingIndex: Int) {
        editingBindingIndex = bindingIndex
        val binding = zones[editingZoneIndex].bindings[bindingIndex]

        container.removeAllViews()
        val editor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        editor.addView(makeTextButton("\u2190 Back") { openZoneDetail(editingZoneIndex) })
        editor.addView(makeLabel(binding.displayName, size = 15f, bottomPad = 12))
        editor.addView(buildAxisToggle(binding))
        editor.addView(buildInvertToggle(binding))
        editor.addView(buildStrengthSlider(binding))
        editor.addView(buildMaxValueSlider(binding))

        container.addView(editor)
    }

    private fun buildAxisToggle(binding: ParameterBinding): View {
        val row = makeRow(bottomPad = 12)
        row.addView(makeLabel("Drag axis: ", size = 13f, bold = false, topPad = 0, bottomPad = 0))

        val btnH = makePillButton("\u2194 Horizontal",
            if (binding.axis == DragAxis.HORIZONTAL) accentColor else Color.TRANSPARENT, textOnPanel) {}
        val btnV = makePillButton("\u2195 Vertical",
            if (binding.axis == DragAxis.VERTICAL) accentColor else Color.TRANSPARENT, textOnPanel) {}

        fun select(axis: DragAxis) {
            (btnH.background as? GradientDrawable)
                ?.setColor(if (axis == DragAxis.HORIZONTAL) accentColor else Color.TRANSPARENT)
            (btnV.background as? GradientDrawable)
                ?.setColor(if (axis == DragAxis.VERTICAL) accentColor else Color.TRANSPARENT)
            updateBinding { it.copy(axis = axis) }
        }
        btnH.setOnClickListener { select(DragAxis.HORIZONTAL) }
        btnV.setOnClickListener { select(DragAxis.VERTICAL) }

        row.addView(btnH, wrapWithMarginEnd(6))
        row.addView(btnV)
        return row
    }

    private fun buildInvertToggle(binding: ParameterBinding): View {
        val isInverted = binding.strength < 0f
        val row = makeRow(bottomPad = 8)
        row.addView(makeLabel("Inverted: ", size = 13f, bold = false, topPad = 0, bottomPad = 0))

        val btn = makePillButton(
            if (isInverted) "ON" else "OFF",
            if (isInverted) accentColor else Color.TRANSPARENT,
            textOnPanel
        ) {}
        btn.setOnClickListener {
            updateBinding { it.copy(strength = -it.strength) }
            // Refresh the editor to update the strength slider
            openBindingEditor(editingBindingIndex)
        }
        row.addView(btn)
        return row
    }

    private fun buildStrengthSlider(binding: ParameterBinding): View {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply {
            text = "Strength: ${(binding.strength * 100).toInt()}%"
            setTextColor(accentColor); textSize = 15f
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        layout.addView(label)
        layout.addView(makeSeekBar(max = 200,
            progress = ((binding.strength + 1f) * 100f).toInt().coerceIn(0, 200)
        ) { progress ->
            val strength = (progress - 100) / 100f
            label.text = "Strength: ${(strength * 100).toInt()}%"
            updateBinding { it.copy(strength = strength) }
        })
        return layout
    }

    private fun buildMaxValueSlider(binding: ParameterBinding): View {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = makeLabel("Max value: ${binding.maxValue.toInt()}", size = 13f, bold = false)
        layout.addView(label)
        layout.addView(makeSeekBar(max = 60, progress = binding.maxValue.toInt().coerceIn(0, 60)) { progress ->
            val mv = progress.coerceAtLeast(1).toFloat()
            label.text = "Max value: ${mv.toInt()}"
            updateBinding { it.copy(maxValue = mv) }
        })
        return layout
    }

    // ── Parameter Picker ────────────────────────────────────────────────

    private fun openParameterPicker() {
        val params = managerProvider()?.parameterList ?: return
        val existingIds = zones[editingZoneIndex].bindings.map { it.paramId }.toSet()

        container.removeAllViews()
        val picker = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        picker.addView(makeTextButton("\u2190 Cancel") { openZoneDetail(editingZoneIndex) })
        picker.addView(makeLabel("Choose a parameter", size = 14f))

        val searchField = makeRoundedEditText("").apply {
            hint = "Search..."; setHintTextColor(dimWhite); textSize = 13f
        }
        picker.addView(searchField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, (8 * dp).toInt()) })

        val cellWrappers = mutableListOf<Pair<String, View>>()
        for (param in params) {
            if (param.max - param.min <= 0f) continue
            val alreadyAdded = param.id in existingIds
            val cell = buildParameterCell(param, alreadyAdded)
            cellWrappers.add(param.displayName.lowercase() to cell)
            picker.addView(cell)
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

    private fun buildParameterCell(param: LAppLive2DManager.ParameterInfo, alreadyAdded: Boolean): View {
        val cell = makeRow(hPad = padH, vPad = 10).apply {
            isClickable = !alreadyAdded
            isFocusable = !alreadyAdded
            if (!alreadyAdded) {
                setOnClickListener {
                    addBindingForParam(param)
                    openZoneDetail(editingZoneIndex)
                }
            }
        }

        cell.addView(TextView(context).apply {
            text = param.displayName
            setTextColor(if (alreadyAdded) dimWhite else textOnPanel)
            textSize = 13f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (alreadyAdded) {
            cell.addView(makeHint("added", topPad = 0, bottomPad = 0))
        }

        return wrapWithDivider(cell)
    }

    private fun addBindingForParam(param: LAppLive2DManager.ParameterInfo) {
        val isAngleParam = param.id.contains("Angle", ignoreCase = true)
        val defaultStrength = if (param.id.contains("AngleY") || param.id.contains("EyeBallY")) -1.0f else 1.0f
        val defaultMax = if (isAngleParam) 30f else (param.max - param.min) / 2f

        updateZone { zone ->
            zone.copy(bindings = zone.bindings + ParameterBinding(
                paramId = param.id,
                displayName = param.displayName,
                axis = DragAxis.HORIZONTAL,
                strength = defaultStrength,
                maxValue = defaultMax.coerceAtLeast(1f)
            ))
        }
    }

    // ── Region Visibility Overlay ───────────────────────────────────────

    private fun showRegionOverlay() {
        val host = overlayHost ?: return
        hideRegionOverlay()
        val overlay = ZoneVisualOverlayView(context, zones, modelScale, modelOffsetX, modelOffsetY)
        regionOverlay = overlay
        host.addView(overlay, 1, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun hideRegionOverlay() {
        regionOverlay?.let { overlayHost?.removeView(it) }
        regionOverlay = null
    }

    private fun refreshRegionOverlay() {
        regionOverlay?.updateZones(zones)
    }

    // ── Zone Position Editor Overlay ────────────────────────────────────

    private fun showZoneOverlay(index: Int) {
        val host = overlayHost ?: return
        dismissOverlay()
        onPanelVisibility(false)
        onHintVisibility(false)

        val zone = zones[index]
        val overlay = HitZoneOverlayView(
            context = context,
            zoneModelNorm = zone.rect,
            zoneColor = zone.color,
            modelScale = modelScale,
            modelOffsetX = modelOffsetX,
            modelOffsetY = modelOffsetY,
            onZoneChanged = { newRect ->
                zones[index] = zones[index].copy(rect = newRect)
                refreshRegionOverlay()
            }
        )
        activeOverlay = overlay
        overlayPreEditRect = RectF(zone.rect)

        host.addView(overlay, host.childCount - 1, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val btnRow = makeRow().apply { tag = "zone_overlay_done_btn" }

        btnRow.addView(makePillButton("Cancel", Color.TRANSPARENT, textOnPanel) {
            overlayPreEditRect?.let { zones[index] = zones[index].copy(rect = it) }
            dismissOverlay()
        }, wrapWithMarginEnd(6))

        btnRow.addView(makePillButton("Centre", Color.TRANSPARENT, textOnPanel) {
            val screenCentre = ZoneTransform.screenToModel(
                RectF(0.5f, 0.5f, 0.5f, 0.5f), modelScale, modelOffsetX, modelOffsetY
            )
            val w = zones[index].rect.width()
            val h = zones[index].rect.height()
            zones[index] = zones[index].copy(rect = RectF(
                screenCentre.left - w / 2f, screenCentre.top - h / 2f,
                screenCentre.left + w / 2f, screenCentre.top + h / 2f
            ))
            refreshRegionOverlay()
            dismissOverlay()
            showZoneOverlay(index)
        }, wrapWithMarginEnd(6))

        btnRow.addView(makePillButton("Done", accentColor, textOnPanel) {
            overlayPreEditRect = null; dismissOverlay(); saveZones()
        })

        overlayDoneBtn = btnRow
        host.addView(btnRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (48 * dp).toInt()
        })
    }

    // ── Zone/Binding Mutation Helpers ────────────────────────────────────

    private fun updateZone(transform: (InteractionZone) -> InteractionZone) {
        zones[editingZoneIndex] = transform(zones[editingZoneIndex])
    }

    private fun updateZoneBinding(bindingIndex: Int, transform: (ParameterBinding) -> ParameterBinding) {
        updateZone { zone ->
            val updated = zone.bindings.toMutableList().apply {
                set(bindingIndex, transform(this[bindingIndex]))
            }
            zone.copy(bindings = updated)
        }
    }

    private fun updateBinding(transform: (ParameterBinding) -> ParameterBinding) {
        updateZoneBinding(editingBindingIndex, transform)
        saveZones()
    }

    private fun addNewZone() {
        zones.add(InteractionZone(
            id = UUID.randomUUID().toString(),
            name = "Zone ${zones.size + 1}",
            color = ZoneRepository.nextColor(zones),
            rect = RectF(0.30f, 0.40f, 0.70f, 0.60f),
            bindings = emptyList(),
            spring = ZoneRepository.defaultSpring()
        ))
        saveZones()
        openZoneDetail(zones.size - 1)
    }

    private fun saveZones() {
        ZoneRepository.saveZones(context, zones)
        onZonesSaved(zones)
        refreshRegionOverlay()
    }

    // ── UI Factory Helpers ──────────────────────────────────────────────

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
        maxLines: Int = Int.MAX_VALUE,
        topPad: Int = 0,
        bottomPad: Int = 4
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(dimWhite)
        textSize = 11f
        this.maxLines = maxLines
        setPadding(0, (topPad * dp).toInt(), 0, (bottomPad * dp).toInt())
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

    private fun makeTextButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label; setTextColor(accentColor); textSize = 14f
        setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun makeDivider(topMargin: Int = (8 * dp).toInt()): View = View(context).apply {
        setBackgroundColor(dividerColor)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ).apply { setMargins(0, topMargin, 0, (8 * dp).toInt()) }
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

    private fun centredWrap(topMargin: Int = 0, bottomMargin: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setMargins(0, (topMargin * dp).toInt(), 0, (bottomMargin * dp).toInt())
    }
}

/** Symbol for display (↔ or ↕). */
private val DragAxis.symbol: String
    get() = if (this == DragAxis.HORIZONTAL) "\u2194" else "\u2195"

/** Toggle to the other axis. */
private val DragAxis.toggled: DragAxis
    get() = if (this == DragAxis.HORIZONTAL) DragAxis.VERTICAL else DragAxis.HORIZONTAL
