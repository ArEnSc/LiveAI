package com.example.liveai

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.widget.ProgressBar
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.liveai.audio.AudioDrivenMotion
import com.example.liveai.audio.AudioMotionConfig
import com.example.liveai.audio.AudioVolumeSource
import com.example.liveai.live2d.CubismLifecycleManager
import com.example.liveai.live2d.LAppLive2DManager
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.example.liveai.live2d.LAppPal
import com.example.liveai.live2d.Live2DSession
import com.example.liveai.live2d.Live2DSessionFactory
import com.example.liveai.live2d.GlStateGuard
import com.example.liveai.live2d.ModelConfig
import com.example.liveai.live2d.PostProcessFilter
import com.example.liveai.interaction.ZoneEditorController
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class WallpaperSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveAI-Setup"
        const val PREFS_NAME = "wallpaper_settings"
        const val KEY_SCALE = "model_scale"
        const val KEY_OFFSET_X = "model_offset_x"
        const val KEY_OFFSET_Y = "model_offset_y"
        const val EXTRA_MODE = "setup_mode"
        const val MODE_WALLPAPER = "wallpaper"
        const val MODE_OVERLAY = "overlay"
        const val PARAM_OVERRIDES_PREFS = "param_overrides"
    }

    private var setupMode = MODE_WALLPAPER

    private var glSurfaceView: GLSurfaceView? = null
    private var live2DManager: LAppLive2DManager? = null
    private val postProcess = PostProcessFilter()
    private var audioVolumeSource: AudioVolumeSource? = null
    private var audioDrivenMotion: AudioDrivenMotion? = null
    private var session: Live2DSession? = null
    private var loadingOverlay: LinearLayout? = null

    private var paramOverrides = mutableMapOf<String, Float>()
    private var paramsContainer: LinearLayout? = null

    private var modelScale = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f
    private var audioMotionEnabled = true
    private var audioMotionIntensity = 1.0f
    private var audioMotionSpeed = 1.0f
    private var rootLayout: FrameLayout? = null
    private var zoneEditorController: ZoneEditorController? = null
    private var controlsPanel: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_WALLPAPER

        // Load existing settings
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        modelScale = prefs.getFloat(KEY_SCALE, 1.0f)
        offsetX = prefs.getFloat(KEY_OFFSET_X, 0.0f)
        offsetY = prefs.getFloat(KEY_OFFSET_Y, 0.0f)

        FilterSettings.loadInto(this, postProcess)

        val filterPrefs = getSharedPreferences(FilterSettings.PREFS_NAME, Context.MODE_PRIVATE)
        audioMotionEnabled = filterPrefs.getBoolean(FilterSettings.KEY_AUDIO_MOTION_ENABLED, true)
        audioMotionIntensity = filterPrefs.getFloat(FilterSettings.KEY_AUDIO_MOTION_INTENSITY, 1.0f)
        audioMotionSpeed = filterPrefs.getFloat(FilterSettings.KEY_AUDIO_MOTION_SPEED, 1.0f)

        // Load saved parameter overrides
        val paramPrefs = getSharedPreferences(PARAM_OVERRIDES_PREFS, Context.MODE_PRIVATE)
        paramOverrides = paramPrefs.all
            .filterValues { it is Float }
            .mapValues { it.value as Float }
            .toMutableMap()

        val root = FrameLayout(this)
        rootLayout = root

        // Session and Cubism init are deferred to the GL thread (onSurfaceCreated)
        // to avoid EGL context conflicts with a running wallpaper engine.

        val renderer = SetupRenderer()

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        root.addView(glSurfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Loading overlay — shown until model loads
        val dp = resources.displayMetrics.density
        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_loading_background))

            val spinner = ProgressBar(this@WallpaperSetupActivity).apply {
                isIndeterminate = true
            }
            addView(spinner, LinearLayout.LayoutParams(
                (48 * dp).toInt(), (48 * dp).toInt()
            ).apply { gravity = Gravity.CENTER_HORIZONTAL })

            val label = TextView(this@WallpaperSetupActivity).apply {
                text = "Loading model..."
                setTextColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.text_on_panel))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, (16 * dp).toInt(), 0, 0)
            }
            addView(label)
        }
        root.addView(loadingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Hint text floating above panel
        val hintLabel = TextView(this).apply {
            text = "Drag to move  |  Pinch to zoom"
            setTextColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.text_on_panel_hint))
            textSize = 12f
            gravity = Gravity.CENTER
            val hintBg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_hint_background))
                cornerRadius = 20 * dp
            }
            background = hintBg
            setPadding((16 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (6 * dp).toInt())
        }
        val hintParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (48 * dp).toInt()
        }
        root.addView(hintLabel, hintParams)

        // Controls panel
        controlsPanel = buildControlsPanel()
        val controlsPanel = controlsPanel!!
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(controlsPanel, panelParams)

        setContentView(root)
        setupTouchHandling()
    }

    private fun buildControlsPanel(): LinearLayout {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val padV = (12 * dp).toInt()
        // Pull colors from resources to match Theme.LiveAI
        val accentColor = ContextCompat.getColor(this, R.color.purple_200)
        val textOnPanel = ContextCompat.getColor(this, R.color.text_on_panel)
        val dimWhite = ContextCompat.getColor(this, R.color.text_on_panel_dim)
        val dividerColor = ContextCompat.getColor(this, R.color.panel_divider)

        // --- Helper functions ---

        fun makeSliderRow(
            label: String,
            initialValue: Float,
            formatValue: (Float) -> String,
            min: Float,
            max: Float,
            steps: Int,
            onChange: (Float, TextView) -> Unit
        ): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
            }
            val lbl = TextView(this).apply {
                text = label
                setTextColor(dimWhite)
                textSize = 13f
            }
            row.addView(lbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f))

            val valueLabel = TextView(this).apply {
                text = formatValue(initialValue)
                setTextColor(textOnPanel)
                textSize = 13f
                gravity = Gravity.END
                minWidth = (36 * dp).toInt()
            }

            val slider = SeekBar(this).apply {
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

        fun makePillButton(
            label: String,
            fillColor: Int,
            textColor: Int,
            onClick: () -> Unit
        ): Button = this@WallpaperSetupActivity.makePillButton(label, fillColor, textColor, dp, onClick)

        // --- Panel background with rounded top corners ---
        val panelBg = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_background))
            cornerRadii = floatArrayOf(
                20 * dp, 20 * dp, 20 * dp, 20 * dp,
                0f, 0f, 0f, 0f
            )
        }

        // Outer wrapper (handles background + non-scrolling elements)
        val outerWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg
        }

        // --- Drag handle ---
        val handleBar = View(this).apply {
            val shape = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_drag_handle))
                cornerRadius = 3 * dp
            }
            background = shape
        }
        outerWrapper.addView(handleBar, LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = (10 * dp).toInt()
            bottomMargin = (6 * dp).toInt()
        })

        // --- Tab bar ---
        val tabNames = listOf("Position", "Effects", "Audio", "Interact", "Params")
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        // Tab indicator line sits below the tab text row
        val tabIndicatorBar = FrameLayout(this).apply {
        }
        val tabIndicator = View(this).apply {
            val shape = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 2 * dp
            }
            background = shape
        }

        val tabTextViews = mutableListOf<TextView>()
        val tabContents = mutableListOf<LinearLayout>()

        fun selectTab(index: Int) {
            tabContents.forEachIndexed { i, content ->
                content.visibility = if (i == index) View.VISIBLE else View.GONE
            }
            tabTextViews.forEachIndexed { i, tv ->
                if (i == index) {
                    tv.setTextColor(textOnPanel)
                    tv.setTypeface(null, Typeface.BOLD)
                } else {
                    tv.setTextColor(dimWhite)
                    tv.setTypeface(null, Typeface.NORMAL)
                }
            }
            // Animate indicator position
            val selectedTab = tabTextViews[index]
            selectedTab.post {
                val tabWidth = selectedTab.width
                val tabLeft = selectedTab.left
                tabIndicator.animate()
                    .translationX(tabLeft.toFloat())
                    .setDuration(200)
                    .start()
                val lp = tabIndicator.layoutParams
                if (lp != null) {
                    lp.width = tabWidth
                    tabIndicator.layoutParams = lp
                }
            }
        }

        for ((index, name) in tabNames.withIndex()) {
            val tabText = TextView(this).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTab(index) }
            }
            tabTextViews.add(tabText)
            tabBar.addView(tabText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        outerWrapper.addView(tabBar)

        // Tab indicator
        tabIndicatorBar.addView(tabIndicator, FrameLayout.LayoutParams(
            0, (3 * dp).toInt()
        ))
        outerWrapper.addView(tabIndicatorBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (3 * dp).toInt()
        ))

        // Divider below tabs
        val tabDivider = View(this).apply {
            setBackgroundColor(dividerColor)
        }
        outerWrapper.addView(tabDivider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ))

        // --- Tab content container (scrollable) ---
        val contentHost = FrameLayout(this)

        // ===== TAB: Position & Scale =====
        val positionContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
        }

        val scaleSlider = makeSliderRow(
            label = "Scale",
            initialValue = modelScale,
            formatValue = { "%.1fx".format(it) },
            min = 0.5f,
            max = 10.0f,
            steps = 950
        ) { value, _ ->
            modelScale = value
            live2DManager?.setModelScale(modelScale)
        }
        positionContent.addView(scaleSlider)

        // Center button row
        val positionBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val btnCenterH = makePillButton("Center Horizontal", Color.TRANSPARENT, textOnPanel) {
            offsetX = 0.0f
            live2DManager?.setModelOffset(offsetX, offsetY)
        }
        positionBtnRow.addView(btnCenterH, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, (8 * dp).toInt(), 0) })

        val btnCenterV = makePillButton("Center Vertical", Color.TRANSPARENT, textOnPanel) {
            offsetY = 0.0f
            live2DManager?.setModelOffset(offsetX, offsetY)
        }
        positionBtnRow.addView(btnCenterV)
        positionContent.addView(positionBtnRow)

        tabContents.add(positionContent)
        contentHost.addView(positionContent)

        // ===== TAB: Effects =====
        val effectsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        // Saturation toggle + slider
        val satRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val satSwitch = Switch(this).apply {
            text = "Saturation"
            setTextColor(dimWhite)
            textSize = 13f
            isChecked = postProcess.isSaturationEnabled
            setOnCheckedChangeListener { _, checked ->
                postProcess.isSaturationEnabled = checked
            }
        }
        satRow.addView(satSwitch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        effectsContent.addView(satRow)

        val satSlider = makeSliderRow(
            label = "Amount",
            initialValue = postProcess.saturationAmount,
            formatValue = { "%.1f".format(it) },
            min = 0.0f,
            max = 3.0f,
            steps = 300
        ) { value, _ ->
            postProcess.saturationAmount = value
        }
        effectsContent.addView(satSlider)

        // Spacer
        effectsContent.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (8 * dp).toInt()
        ))

        // Outline toggle + slider
        val outRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val outSwitch = Switch(this).apply {
            text = "Outline"
            setTextColor(dimWhite)
            textSize = 13f
            isChecked = postProcess.isOutlineEnabled
            setOnCheckedChangeListener { _, checked ->
                postProcess.isOutlineEnabled = checked
            }
        }
        outRow.addView(outSwitch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        effectsContent.addView(outRow)

        val outSlider = makeSliderRow(
            label = "Thickness",
            initialValue = postProcess.outlineThickness,
            formatValue = { "%d".format(it.toInt()) },
            min = 0.0f,
            max = 100.0f,
            steps = 100
        ) { value, _ ->
            postProcess.outlineThickness = value
        }
        effectsContent.addView(outSlider)

        // Outline color row
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val colorLabel = TextView(this).apply {
            text = "Color"
            setTextColor(dimWhite)
            textSize = 13f
            setPadding(0, 0, (12 * dp).toInt(), 0)
        }
        colorRow.addView(colorLabel)

        data class ColorPreset(val name: String, val color: Int, val r: Float, val g: Float, val b: Float)
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

        val colorScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val colorInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnSize = (28 * dp).toInt()
        val btnMargin = (5 * dp).toInt()
        for (preset in presets) {
            val colorBtn = View(this).apply {
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.color)
                    setStroke((1.5f * dp).toInt(), ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_btn_outline))
                }
                background = shape
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
        effectsContent.addView(colorRow)

        tabContents.add(effectsContent)
        contentHost.addView(effectsContent)

        // ===== TAB: Audio Motion =====
        val audioContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        val audioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val audioSwitch = Switch(this).apply {
            text = "Enabled"
            setTextColor(dimWhite)
            textSize = 13f
            isChecked = audioMotionEnabled
            setOnCheckedChangeListener { _, checked ->
                audioMotionEnabled = checked
                updateAudioMotionConfig()
            }
        }
        audioRow.addView(audioSwitch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val volumeMeter = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        audioRow.addView(volumeMeter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val volumeLabel = TextView(this).apply {
            text = "0%"
            setTextColor(dimWhite)
            textSize = 12f
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        audioRow.addView(volumeLabel)
        audioContent.addView(audioRow)

        val volumeHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val volumeUpdater = object : Runnable {
            override fun run() {
                val vol = ((audioVolumeSource?.volume ?: 0f) * 100).toInt()
                volumeMeter.progress = vol
                volumeLabel.text = "${vol}%"
                volumeHandler.postDelayed(this, 100)
            }
        }
        volumeHandler.post(volumeUpdater)

        val intSlider = makeSliderRow(
            label = "Intensity",
            initialValue = audioMotionIntensity,
            formatValue = { "%.1f".format(it) },
            min = 0.0f,
            max = 3.0f,
            steps = 300
        ) { value, _ ->
            audioMotionIntensity = value
            updateAudioMotionConfig()
        }
        audioContent.addView(intSlider)

        val spdSlider = makeSliderRow(
            label = "Speed",
            initialValue = audioMotionSpeed,
            formatValue = { "%.1f".format(it) },
            min = 0.0f,
            max = 3.0f,
            steps = 300
        ) { value, _ ->
            audioMotionSpeed = value
            updateAudioMotionConfig()
        }
        audioContent.addView(spdSlider)

        tabContents.add(audioContent)
        contentHost.addView(audioContent)

        // ===== TAB: Interaction =====
        val interactContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        // --- Zone Editor ---

        val zoneContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        interactContent.addView(zoneContainer)

        zoneEditorController = ZoneEditorController(
            context = this,
            container = zoneContainer,
            managerProvider = { live2DManager },
            overlayHost = rootLayout,
            onPanelVisibility = { visible ->
                controlsPanel?.visibility = if (visible) View.VISIBLE else View.GONE
            },
            onZonesSaved = { /* zones auto-persist via ZoneRepository */ }
        )
        zoneEditorController?.buildZoneList()

        tabContents.add(interactContent)
        contentHost.addView(interactContent)

        // ===== TAB: Parameters =====
        val paramsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        val paramsLoadingLabel = TextView(this).apply {
            text = "Loading model parameters..."
            setTextColor(dimWhite)
            textSize = 13f
        }
        paramsContent.addView(paramsLoadingLabel)
        paramsContainer = paramsContent

        tabContents.add(paramsContent)
        contentHost.addView(paramsContent)

        // Wrap content in ScrollView
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(contentHost)
        }
        val maxContentHeight = (resources.displayMetrics.heightPixels * 0.30f).toInt()
        outerWrapper.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        scrollView.post {
            if (scrollView.height > maxContentHeight) {
                scrollView.layoutParams = scrollView.layoutParams.apply {
                    height = maxContentHeight
                }
            }
        }

        // ===== Action buttons (pinned below scroll) =====
        val btnDivider = View(this).apply {
            setBackgroundColor(dividerColor)
        }
        outerWrapper.addView(btnDivider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ))

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, (10 * dp).toInt(), padH, (16 * dp).toInt())
        }

        val btnReset = makePillButton("Reset All", Color.TRANSPARENT, textOnPanel) {
            modelScale = 1.0f
            offsetX = 0.0f
            offsetY = 0.0f
            live2DManager?.setModelScale(modelScale)
            live2DManager?.setModelOffset(offsetX, offsetY)
            postProcess.isSaturationEnabled = false
            postProcess.isOutlineEnabled = false
            postProcess.saturationAmount = 1.5f
            postProcess.outlineThickness = 2f
            satSwitch.isChecked = false
            outSwitch.isChecked = false
            (scaleSlider.getChildAt(1) as? SeekBar)?.progress = 50
            (satSlider.getChildAt(1) as? SeekBar)?.progress = 150
            (outSlider.getChildAt(1) as? SeekBar)?.progress = 2
        }
        btnRow.addView(btnReset, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0) })

        val applyLabel = if (setupMode == MODE_OVERLAY) "Apply to Overlay" else "Apply Wallpaper"
        val btnApply = makePillButton(applyLabel, accentColor, textOnPanel) {
            applySettings()
        }
        btnRow.addView(btnApply, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0) })

        outerWrapper.addView(btnRow)

        // Select first tab
        selectTab(0)

        return outerWrapper
    }

    private fun setupTouchHandling() {
        var lastTouchX = 0f
        var lastTouchY = 0f
        var isScaling = false
        var activePointerId = MotionEvent.INVALID_POINTER_ID

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    modelScale *= detector.scaleFactor
                    modelScale = modelScale.coerceIn(0.5f, 10.0f)
                    live2DManager?.setModelScale(modelScale)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                }
            }
        )

        glSurfaceView?.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    val view = glSurfaceView ?: return@setOnTouchListener true
                    val dragX = (event.x / view.width) * 2f - 1f
                    val dragY = -((event.y / view.height) * 2f - 1f)
                    live2DManager?.onDrag(dragX, dragY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isScaling && event.pointerCount == 1) {
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex >= 0) {
                            val dx = event.getX(pointerIndex) - lastTouchX
                            val dy = event.getY(pointerIndex) - lastTouchY

                            val view = glSurfaceView ?: return@setOnTouchListener true
                            offsetX += (dx / view.width) * 2.0f
                            offsetY -= (dy / view.height) * 2.0f
                            live2DManager?.setModelOffset(offsetX, offsetY)

                            // Feed touch position to model drag for physics
                            val dragX = (event.getX(pointerIndex) / view.width) * 2f - 1f
                            val dragY = -((event.getY(pointerIndex) / view.height) * 2f - 1f)
                            live2DManager?.onDrag(dragX, dragY)

                            lastTouchX = event.getX(pointerIndex)
                            lastTouchY = event.getY(pointerIndex)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        val newIndex = if (pointerIndex == 0) 1 else 0
                        activePointerId = event.getPointerId(newIndex)
                        lastTouchX = event.getX(newIndex)
                        lastTouchY = event.getY(newIndex)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    live2DManager?.onDrag(0f, 0f)
                    true
                }
                else -> true
            }
        }
    }

    private fun applySettings() {
        // Save position/scale settings
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_SCALE, modelScale)
            .putFloat(KEY_OFFSET_X, offsetX)
            .putFloat(KEY_OFFSET_Y, offsetY)
            .apply()

        // Save filter settings
        val outColor = postProcess.outlineColor
        getSharedPreferences(FilterSettings.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(FilterSettings.KEY_SATURATION, postProcess.isSaturationEnabled)
            .putBoolean(FilterSettings.KEY_OUTLINE, postProcess.isOutlineEnabled)
            .putFloat(FilterSettings.KEY_SATURATION_AMOUNT, postProcess.saturationAmount)
            .putFloat(FilterSettings.KEY_OUTLINE_THICKNESS, postProcess.outlineThickness)
            .putFloat(FilterSettings.KEY_OUTLINE_COLOR_R, outColor[0])
            .putFloat(FilterSettings.KEY_OUTLINE_COLOR_G, outColor[1])
            .putFloat(FilterSettings.KEY_OUTLINE_COLOR_B, outColor[2])
            .putBoolean(FilterSettings.KEY_AUDIO_MOTION_ENABLED, audioMotionEnabled)
            .putFloat(FilterSettings.KEY_AUDIO_MOTION_INTENSITY, audioMotionIntensity)
            .putFloat(FilterSettings.KEY_AUDIO_MOTION_SPEED, audioMotionSpeed)
            .apply()

        // Save parameter overrides
        val paramEditor = getSharedPreferences(PARAM_OVERRIDES_PREFS, Context.MODE_PRIVATE).edit()
        paramEditor.clear()
        for ((key, value) in paramOverrides) {
            paramEditor.putFloat(key, value)
        }
        paramEditor.apply()

        Log.d(TAG, "Settings saved: mode=$setupMode scale=$modelScale params=${paramOverrides.size} overrides")

        if (setupMode == MODE_OVERLAY) {
            launchOverlayService()
        } else {
            // Destroy GL resources on the GL thread before stopping it.
            // Doing this on the main thread would delete resources from
            // whatever EGL context is current there (e.g. the wallpaper engine's).
            destroySessionOnGlThread()

            // Launch wallpaper picker
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@WallpaperSetupActivity, Live2DWallpaperService::class.java)
                )
            }
            startActivity(intent)
            finish()
        }
    }

    /** The parameter list view (shown by default). */
    private var paramListView: LinearLayout? = null
    /** The single-parameter editor view (shown when a param is tapped). */
    private var paramEditorView: LinearLayout? = null

    private fun populateParamsTab(params: List<LAppLive2DManager.ParameterInfo>) {
        val container = paramsContainer ?: return
        container.removeAllViews()

        if (params.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No parameters found"
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 13f
            })
            return
        }

        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val accentColor = ContextCompat.getColor(this, R.color.purple_200)
        val textOnPanel = ContextCompat.getColor(this, R.color.text_on_panel)
        val dimWhite = ContextCompat.getColor(this, R.color.text_on_panel_dim)
        val dividerColor = ContextCompat.getColor(this, R.color.panel_divider)

        // --- Parameter list view ---
        val listView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        paramListView = listView

        // --- Search field ---
        val searchField = EditText(this).apply {
            hint = "Search parameters..."
            setHintTextColor(dimWhite)
            setTextColor(textOnPanel)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_background))
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

        // --- Editor view (hidden initially) ---
        val editorView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        paramEditorView = editorView

        // Track cell wrappers for search filtering
        val cellWrappers = mutableListOf<Pair<String, View>>() // displayName to wrapper

        // Build list cells
        for (param in params) {
            val range = param.max - param.min
            if (range <= 0f) continue

            val hasOverride = paramOverrides.containsKey(param.id)
            val initialValue = if (hasOverride) {
                paramOverrides[param.id] ?: param.defaultValue
            } else {
                param.defaultValue
            }

            // --- Cell row ---
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, (12 * dp).toInt(), padH, (12 * dp).toInt())
                isClickable = true
                isFocusable = true
            }

            val nameLabel = TextView(this).apply {
                text = param.displayName
                setTextColor(if (hasOverride) textOnPanel else dimWhite)
                textSize = 13f
                setTypeface(null, if (hasOverride) Typeface.BOLD else Typeface.NORMAL)
            }
            cell.addView(nameLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val cellValue = TextView(this).apply {
                text = "%.2f".format(initialValue)
                setTextColor(if (hasOverride) accentColor else dimWhite)
                textSize = 13f
                gravity = Gravity.END
            }
            cell.addView(cellValue)

            // Chevron
            val chevron = TextView(this).apply {
                text = "  \u203A"
                setTextColor(dimWhite)
                textSize = 16f
            }
            cell.addView(chevron)

            // Divider
            val divider = View(this).apply { setBackgroundColor(dividerColor) }

            val cellWrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(cell)
                addView(divider, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ))
            }

            // Tap → open editor for this param
            cell.setOnClickListener {
                openParamEditor(
                    param, initialValue, nameLabel, cellValue,
                    accentColor, textOnPanel, dimWhite, dividerColor, dp
                )
            }

            cellWrappers.add(param.displayName.lowercase() to cellWrapper)
            listView.addView(cellWrapper)
        }

        // Wire up search filtering
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

        container.addView(listView)
        container.addView(editorView)

        Log.d(TAG, "Params tab populated with ${params.size} parameters")
    }

    private fun openParamEditor(
        param: LAppLive2DManager.ParameterInfo,
        currentValue: Float,
        nameLabel: TextView,
        cellValue: TextView,
        accentColor: Int,
        textOnPanel: Int,
        dimWhite: Int,
        dividerColor: Int,
        dp: Float
    ) {
        val editor = paramEditorView ?: return
        val list = paramListView ?: return
        val range = param.max - param.min
        val steps = 200
        val padH = (16 * dp).toInt()

        // Snapshot the value before editing so Cancel can revert
        val valueBefore = paramOverrides[param.id] ?: param.defaultValue

        editor.removeAllViews()

        // --- Title ---
        val title = TextView(this).apply {
            text = param.displayName
            setTextColor(textOnPanel)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(padH, (12 * dp).toInt(), padH, (4 * dp).toInt())
        }
        editor.addView(title)

        // --- Value display ---
        val valueDisplay = TextView(this).apply {
            text = "%.2f".format(currentValue)
            setTextColor(accentColor)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(padH, (4 * dp).toInt(), padH, (8 * dp).toInt())
        }
        editor.addView(valueDisplay)

        // --- Slider with min/max ---
        val sliderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, 0, padH, (4 * dp).toInt())
        }

        val minLabel = TextView(this).apply {
            text = "%.1f".format(param.min)
            setTextColor(dimWhite)
            textSize = 11f
        }
        sliderRow.addView(minLabel)

        val slider = SeekBar(this).apply {
            max = steps
            progress = ((currentValue - param.min) / range * steps).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = param.min + (progress.toFloat() / steps) * range
                    valueDisplay.text = "%.2f".format(value)
                    paramOverrides[param.id] = value
                    glSurfaceView?.queueEvent {
                        live2DManager?.setParameterOverride(param.id, value)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        sliderRow.addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val maxLabel = TextView(this).apply {
            text = "%.1f".format(param.max)
            setTextColor(dimWhite)
            textSize = 11f
        }
        sliderRow.addView(maxLabel)

        editor.addView(sliderRow)

        // --- Divider ---
        editor.addView(View(this).apply { setBackgroundColor(dividerColor) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
        ).apply { topMargin = (8 * dp).toInt() })

        // --- Button row: Reset | Cancel | Done ---
        val btnRow = LinearLayout(this).apply {
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

        val btnReset = makePillButton("Reset", Color.TRANSPARENT, dimWhite, dp) {
            paramOverrides.remove(param.id)
            slider.progress = ((param.defaultValue - param.min) / range * steps).toInt()
            valueDisplay.text = "%.2f".format(param.defaultValue)
            nameLabel.setTextColor(dimWhite)
            nameLabel.setTypeface(null, Typeface.NORMAL)
            cellValue.text = "%.2f".format(param.defaultValue)
            cellValue.setTextColor(dimWhite)
            glSurfaceView?.queueEvent {
                live2DManager?.clearParameterOverride(param.id)
            }
            closeEditor()
        }
        btnRow.addView(btnReset, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) })

        val btnCancel = makePillButton("Cancel", Color.TRANSPARENT, dimWhite, dp) {
            // Revert to value before editing
            if (valueBefore == param.defaultValue && !paramOverrides.containsKey(param.id)) {
                // Was at default, no override existed
                glSurfaceView?.queueEvent {
                    live2DManager?.clearParameterOverride(param.id)
                }
            } else {
                paramOverrides[param.id] = valueBefore
                glSurfaceView?.queueEvent {
                    live2DManager?.setParameterOverride(param.id, valueBefore)
                }
            }
            cellValue.text = "%.2f".format(valueBefore)
            closeEditor()
        }
        btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) })

        val btnDone = makePillButton("Done", accentColor, textOnPanel, dp) {
            // Keep current value — update the cell to reflect it
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

        // Animate: list fades out, editor slides down into view
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

    private fun makePillButton(
        label: String,
        fillColor: Int,
        textColor: Int,
        dp: Float,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 13f
            isAllCaps = false
            val shape = GradientDrawable().apply {
                cornerRadius = 24 * dp
                setColor(fillColor)
                if (fillColor == Color.TRANSPARENT) {
                    setStroke((1.5f * dp).toInt(), ContextCompat.getColor(this@WallpaperSetupActivity, R.color.panel_btn_outline))
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


    private fun updateAudioMotionConfig() {
        audioDrivenMotion?.config = AudioMotionConfig(
            enabled = audioMotionEnabled,
            intensity = audioMotionIntensity,
            speed = audioMotionSpeed
        )
    }

    private fun launchOverlayService() {
        destroySessionOnGlThread()

        OverlayService.requestRestart(this) {
            val intent = Intent(this, OverlayService::class.java)
            startForegroundService(intent)
        }
        finish()
    }

    /**
     * Destroy the Live2D session on the GL thread so that glDelete* calls
     * target the GLSurfaceView's EGL context — not the wallpaper engine's
     * context which may be current on the main thread.
     */
    private fun destroySessionOnGlThread() {
        val s = session ?: return
        session = null
        glSurfaceView?.queueEvent {
            Live2DSessionFactory.destroy(s)
            Log.d(TAG, "Session destroyed on GL thread")
        }
        glSurfaceView?.onPause()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Session may already be destroyed by destroySessionOnGlThread.
        // If not (e.g. back pressed without apply), clean up here.
        // The GL thread is already stopped by onPause, so we can't
        // queue to it — but at this point the activity's EGL context
        // is also gone, so the glDelete calls are harmless no-ops.
        if (session != null) {
            try {
                session?.let { Live2DSessionFactory.destroy(it) }
            } catch (e: Exception) {
                Log.w(TAG, "onDestroy: session destroy failed", e)
            }
            session = null
        }
    }

    inner class SetupRenderer : GLSurfaceView.Renderer {

        private var modelLoaded = false
        private var setupFrameCount = 0L

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            Log.d(TAG, "SetupRenderer.onSurfaceCreated")
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            // Ensure framework is started (idempotent). Shaders are per-thread
            // (ThreadLocal), so this GL context gets its own compiled programs.
            CubismLifecycleManager.ensureStarted(this@WallpaperSetupActivity)
            // Clear any stale shaders from a previous EGL context on this GL thread
            CubismRendererAndroid.reloadShader()

            session = Live2DSessionFactory.create(this@WallpaperSetupActivity)
            audioVolumeSource = session?.audioSource
            live2DManager = session?.manager
            audioDrivenMotion = session?.audioMotion

            live2DManager?.setFitToScreen(true)
            live2DManager?.setModelScale(modelScale)
            live2DManager?.setModelOffset(offsetX, offsetY)
            if (paramOverrides.isNotEmpty()) {
                live2DManager?.setAllParameterOverrides(paramOverrides)
            }
            Log.d(TAG, "SetupRenderer: session created on GL thread, manager=${live2DManager != null}")

            postProcess.init()

        }

        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            Log.d(TAG, "SetupRenderer.onSurfaceChanged ${width}x${height} modelLoaded=$modelLoaded manager=${live2DManager != null}")
            GLES20.glViewport(0, 0, width, height)
            live2DManager?.setWindowSize(width, height)
            postProcess.resize(width, height)

            if (!modelLoaded) {
                Log.d(TAG, "SetupRenderer: loading model...")
                live2DManager?.loadModel(ModelConfig.DEFAULT_MODEL_DIR, ModelConfig.DEFAULT_MODEL_FILE)
                val cw = live2DManager?.getCanvasWidth() ?: 0f
                val ch = live2DManager?.getCanvasHeight() ?: 0f
                Log.d(TAG, "SetupRenderer: model loaded, canvas=${cw}x${ch}")
                modelLoaded = true

                // Hide loading overlay and populate params tab on the UI thread
                val paramList = live2DManager?.getParameterList() ?: emptyList()
                loadingOverlay?.post {
                    loadingOverlay?.animate()
                        ?.alpha(0f)
                        ?.setDuration(300)
                        ?.withEndAction { loadingOverlay?.visibility = View.GONE }
                        ?.start()
                    populateParamsTab(paramList)
                }
            }
        }

        override fun onDrawFrame(unused: GL10?) {
            setupFrameCount++
            LAppPal.updateTime()

            // Log mask diagnostics on first few frames for comparison with wallpaper
            if (setupFrameCount <= 3) {
                val diag = live2DManager?.getMaskDiagnostics() ?: "null manager"
                Log.d(TAG, "SetupRenderer mask diagnostics: $diag")
            }

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glClearDepthf(1.0f)

            // Draw model with optional post-processing
            val useFilters = postProcess.isAnyFilterEnabled && postProcess.canCapture()
            if (useFilters) {
                postProcess.beginCapture()
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GlStateGuard.withGuard {
                    live2DManager?.onUpdate()
                }
                postProcess.endCaptureAndApply()
            } else {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GlStateGuard.withGuard {
                    live2DManager?.onUpdate()
                }
            }

            // Check for GL errors after rendering
            if (setupFrameCount <= 5) {
                val glErr = GLES20.glGetError()
                if (glErr != GLES20.GL_NO_ERROR) {
                    Log.e(TAG, "SetupRenderer GL error: 0x${Integer.toHexString(glErr)} at frame $setupFrameCount")
                }
            }
        }

    }
}
