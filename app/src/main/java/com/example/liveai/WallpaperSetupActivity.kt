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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.liveai.interaction.TouchInteractionHandler
import com.example.liveai.interaction.ZoneEditorController
import com.example.liveai.interaction.ZoneRepository
import com.example.liveai.gyroscope.GyroMotionConfig
import com.example.liveai.gyroscope.GyroscopeDrivenMotion
import com.example.liveai.gyroscope.loadGyroMotionConfig
import com.example.liveai.gyroscope.save
import com.example.liveai.setup.AudioTabBuilder
import com.example.liveai.setup.EffectsTabBuilder
import com.example.liveai.setup.GyroscopeTabBuilder
import com.example.liveai.setup.ParameterTabBuilder
import com.example.liveai.setup.PositionTabBuilder
import com.example.liveai.setup.SetupUiHelpers
import com.example.liveai.setup.TtsTabBuilder
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

        private const val TAB_POSITION = 0
        private const val TAB_EFFECTS = 1
        private const val TAB_AUDIO = 2
        private const val TAB_GYRO = 3
        private const val TAB_INTERACT = 4
        private const val TAB_PARAMS = 5
        private const val TAB_TTS = 6
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

    // Tab builders
    private var positionTab: PositionTabBuilder? = null
    private var effectsTab: EffectsTabBuilder? = null
    private var audioTab: AudioTabBuilder? = null
    private var gyroscopeTab: GyroscopeTabBuilder? = null
    private var parameterTab: ParameterTabBuilder? = null
    private var ttsTab: TtsTabBuilder? = null

    private var modelScale = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f
    private var audioMotionEnabled = true
    private var audioMotionIntensity = 1.0f
    private var audioMotionSpeed = 1.0f
    private lateinit var gyroConfig: GyroMotionConfig
    private var gyroscopeDrivenMotion: GyroscopeDrivenMotion? = null
    private var rootLayout: FrameLayout? = null
    private var zoneEditorController: ZoneEditorController? = null
    private var touchHandler: TouchInteractionHandler? = null
    private var hintLabel: TextView? = null
    private var activeTabIndex = 0
    private var panelCollapsed = false
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
        gyroConfig = loadGyroMotionConfig(this)

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

        // Hint text floating above panel — tap to collapse/expand
        hintLabel = TextView(this).apply {
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
            isClickable = true
            isFocusable = true
            setOnClickListener { togglePanel() }
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

        // Pad the bottom of the controls panel so the nav bar doesn't cover the buttons
        ViewCompat.setOnApplyWindowInsetsListener(controlsPanel) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBarInsets.bottom)
            insets
        }

        setupTouchHandling()
    }

    private fun togglePanel() {
        val panel = controlsPanel ?: return
        panelCollapsed = !panelCollapsed
        if (panelCollapsed) {
            panel.visibility = View.GONE
            hintLabel?.text = when (activeTabIndex) {
                TAB_POSITION -> "Drag to move  |  Pinch to zoom  \u2022  Tap to show menu"
                TAB_INTERACT -> "Drag zones to interact  \u2022  Tap to show menu"
                else -> "Tap to show menu"
            }
        } else {
            panel.visibility = View.VISIBLE
            updateHintForTab(activeTabIndex)
        }
    }

    private fun updateHintForTab(index: Int) {
        hintLabel?.text = when (index) {
            TAB_POSITION -> "Drag to move  |  Pinch to zoom  \u2022  Tap to hide menu"
            TAB_INTERACT -> "Drag zones to interact  \u2022  Tap to hide menu"
            else -> "Tap to hide menu"
        }
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

        fun makePillButton(
            label: String,
            fillColor: Int,
            textColor: Int,
            onClick: () -> Unit
        ): Button = SetupUiHelpers.makePillButton(this@WallpaperSetupActivity, label, fillColor, textColor, dp, onClick)

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
        val tabNames = listOf("Position", "Effects", "Audio", "Gyro", "Interact", "Params", "TTS")
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
            if (activeTabIndex == TAB_INTERACT && index != TAB_INTERACT) {
                val zones = ZoneRepository.loadZones(this@WallpaperSetupActivity)
                val allParamIds = zones.flatMap { zone ->
                    zone.bindings.map { it.paramId }
                }.toSet()
                if (allParamIds.isNotEmpty()) {
                    glSurfaceView?.queueEvent {
                        live2DManager?.clearInteractionParams(allParamIds)
                    }
                }
                touchHandler = null
                zoneEditorController?.hideRegionsOnTabChange()
            }

            activeTabIndex = index
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

            if (!panelCollapsed) {
                updateHintForTab(index)
            }

            if (index == TAB_INTERACT) {
                rebuildTouchHandler()
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
        val posBuilder = PositionTabBuilder(
            context = this,
            onScaleChanged = { value ->
                modelScale = value
                live2DManager?.setModelScale(modelScale)
                zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)
            },
            onCenterHorizontal = {
                offsetX = 0.0f
                live2DManager?.setModelOffset(offsetX, offsetY)
                zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)
            },
            onCenterVertical = {
                offsetY = 0.0f
                live2DManager?.setModelOffset(offsetX, offsetY)
                zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)
            },
            initialScale = modelScale
        )
        positionTab = posBuilder
        val positionContent = posBuilder.build()
        tabContents.add(positionContent)
        contentHost.addView(positionContent)

        // ===== TAB: Effects =====
        val effBuilder = EffectsTabBuilder(context = this, postProcess = postProcess)
        effectsTab = effBuilder
        val effectsContent = effBuilder.build()
        tabContents.add(effectsContent)
        contentHost.addView(effectsContent)

        // ===== TAB: Audio Motion =====
        val audioBuilder = AudioTabBuilder(
            context = this,
            audioVolumeSourceProvider = { audioVolumeSource },
            initialEnabled = audioMotionEnabled,
            initialIntensity = audioMotionIntensity,
            initialSpeed = audioMotionSpeed,
            onEnabledChanged = { audioMotionEnabled = it; updateAudioMotionConfig() },
            onIntensityChanged = { audioMotionIntensity = it; updateAudioMotionConfig() },
            onSpeedChanged = { audioMotionSpeed = it; updateAudioMotionConfig() }
        )
        audioTab = audioBuilder
        val audioContent = audioBuilder.build()
        tabContents.add(audioContent)
        contentHost.addView(audioContent)

        // ===== TAB: Gyroscope =====
        val gyroBuilder = GyroscopeTabBuilder(
            context = this,
            initialConfig = gyroConfig,
            onConfigChanged = { config ->
                gyroConfig = config
                gyroscopeDrivenMotion?.config = config
            }
        )
        gyroscopeTab = gyroBuilder
        val gyroContent = gyroBuilder.build()
        tabContents.add(gyroContent)
        contentHost.addView(gyroContent)

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
            onHintVisibility = { visible ->
                hintLabel?.visibility = if (visible) View.VISIBLE else View.GONE
            },
            onZonesSaved = { rebuildTouchHandler() }
        )
        zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)
        zoneEditorController?.buildZoneList()

        tabContents.add(interactContent)
        contentHost.addView(interactContent)

        // ===== TAB: Parameters =====
        val paramBuilder = ParameterTabBuilder(
            context = this,
            paramOverrides = paramOverrides,
            glSurfaceView = { glSurfaceView },
            live2DManager = { live2DManager }
        )
        parameterTab = paramBuilder
        val paramsContent = paramBuilder.buildPlaceholder()
        tabContents.add(paramsContent)
        contentHost.addView(paramsContent)

        // ===== TAB: TTS =====
        val ttsBuilder = TtsTabBuilder(
            context = this,
            glSurfaceView = { glSurfaceView },
            live2DManager = { live2DManager }
        )
        ttsTab = ttsBuilder
        val ttsContent = ttsBuilder.build()
        tabContents.add(ttsContent)
        contentHost.addView(ttsContent)

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
            zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)
            postProcess.isSaturationEnabled = false
            postProcess.isOutlineEnabled = false
            postProcess.saturationAmount = 1.5f
            postProcess.outlineThickness = 2f
            effectsTab?.satSwitch?.isChecked = false
            effectsTab?.outSwitch?.isChecked = false
            (positionTab?.scaleSlider?.getChildAt(1) as? SeekBar)?.progress = 50
            (effectsTab?.satSlider?.getChildAt(1) as? SeekBar)?.progress = 150
            (effectsTab?.outSlider?.getChildAt(1) as? SeekBar)?.progress = 2
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

    private fun rebuildTouchHandler() {
        val mgr = live2DManager ?: run {
            touchHandler = null
            return
        }
        val zones = ZoneRepository.loadZones(this)
        touchHandler = TouchInteractionHandler(mgr, zones)
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
                    zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                }
            }
        )

        glSurfaceView?.setOnTouchListener { _, event ->
            // Interact tab: forward to zone touch handler
            if (activeTabIndex == TAB_INTERACT) {
                return@setOnTouchListener touchHandler?.onTouchEvent(event) == true
            }
            // Only Position tab allows drag/scale
            if (activeTabIndex != TAB_POSITION) return@setOnTouchListener false

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
                            zoneEditorController?.updateModelTransform(modelScale, offsetX, offsetY)

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

        // Save gyroscope config (own prefs file with JSON bindings)
        gyroConfig.save(this)

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

            // Clear existing wallpaper first so the old engine fully tears down
            // before the new one starts, avoiding EGL context conflicts.
            clearExistingWallpaperThenApply()
        }
    }

    private fun clearExistingWallpaperThenApply() {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo
        val ourComponent = ComponentName(this, Live2DWallpaperService::class.java)
        val isOurWallpaperActive = info != null && info.component == ourComponent

        if (isOurWallpaperActive) {
            // Clear the live wallpaper and poll until the engine has torn down
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                } else {
                    wm.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear wallpaper before re-apply", e)
            }
            waitForWallpaperClearThenLaunch(wm, ourComponent, attemptsLeft = 20)
        } else {
            launchWallpaperPicker()
        }
    }

    private fun waitForWallpaperClearThenLaunch(
        wm: WallpaperManager,
        ourComponent: ComponentName,
        attemptsLeft: Int
    ) {
        val currentInfo = wm.wallpaperInfo
        val stillActive = currentInfo != null && currentInfo.component == ourComponent

        if (!stillActive || attemptsLeft <= 0) {
            if (attemptsLeft <= 0) {
                Log.w(TAG, "Wallpaper clear timed out, launching picker anyway")
            }
            launchWallpaperPicker()
        } else {
            // Poll again after 100ms
            Handler(Looper.getMainLooper()).postDelayed({
                waitForWallpaperClearThenLaunch(wm, ourComponent, attemptsLeft - 1)
            }, 100)
        }
    }

    private fun launchWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@WallpaperSetupActivity, Live2DWallpaperService::class.java)
            )
        }
        startActivity(intent)
        finish()
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
     *
     * Uses a CountDownLatch to ensure the GL event completes before
     * onPause() stops the GL thread.
     */
    private fun destroySessionOnGlThread() {
        val s = session ?: return
        session = null
        val latch = java.util.concurrent.CountDownLatch(1)
        glSurfaceView?.queueEvent {
            try {
                postProcess.release()
                Live2DSessionFactory.destroy(s)
                Log.d(TAG, "Session destroyed on GL thread")
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted waiting for GL session destroy", e)
        }
        glSurfaceView?.onPause()
    }

    override fun onPause() {
        super.onPause()
        audioTab?.stopVolumeUpdates()
        destroySessionOnGlThread()  // clean GL teardown before thread stops
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
        live2DManager = null
        audioVolumeSource = null
        audioDrivenMotion = null
        gyroscopeDrivenMotion = null

        // Release TTS
        ttsTab?.release()

        // Release view references so GC can collect them promptly
        glSurfaceView = null
        loadingOverlay = null
        rootLayout = null
        hintLabel = null
        controlsPanel = null
        zoneEditorController = null
        touchHandler = null

        // Release tab builder references
        positionTab = null
        effectsTab = null
        audioTab = null
        parameterTab = null
        ttsTab = null
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
            gyroscopeDrivenMotion = session?.gyroscopeMotion
            gyroscopeDrivenMotion?.config = gyroConfig

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
                    parameterTab?.populate(paramList)
                    gyroscopeTab?.setAvailableParams(paramList)
                }
            }
        }

        override fun onDrawFrame(unused: GL10?) {
            setupFrameCount++
            LAppPal.updateTime()
            touchHandler?.updateSpring()

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
