package com.example.liveai

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.liveai.live2d.LAppDefine
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppPal
import com.example.liveai.live2d.LAppTextureManager
import com.example.liveai.live2d.PostProcessFilter
import com.live2d.sdk.cubism.framework.CubismFramework
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class WallpaperSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveAI-Setup"
        const val PREFS_NAME = "wallpaper_settings"
        const val KEY_SCALE = "model_scale"
        const val KEY_OFFSET_X = "model_offset_x"
        const val KEY_OFFSET_Y = "model_offset_y"
    }

    private var glSurfaceView: GLSurfaceView? = null
    private var live2DManager: LAppLive2DManager? = null
    private val postProcess = PostProcessFilter()

    private var modelScale = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load existing settings
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        modelScale = prefs.getFloat(KEY_SCALE, 1.0f)
        offsetX = prefs.getFloat(KEY_OFFSET_X, 0.0f)
        offsetY = prefs.getFloat(KEY_OFFSET_Y, 0.0f)

        val filterPrefs = getSharedPreferences(FilterSettings.PREFS_NAME, Context.MODE_PRIVATE)
        postProcess.isSaturationEnabled = filterPrefs.getBoolean(FilterSettings.KEY_SATURATION, false)
        postProcess.isOutlineEnabled = filterPrefs.getBoolean(FilterSettings.KEY_OUTLINE, false)
        postProcess.saturationAmount = filterPrefs.getFloat(FilterSettings.KEY_SATURATION_AMOUNT, 1.5f)
        postProcess.outlineThickness = filterPrefs.getFloat(FilterSettings.KEY_OUTLINE_THICKNESS, 1.5f)
        postProcess.setOutlineColor(
            filterPrefs.getFloat(FilterSettings.KEY_OUTLINE_COLOR_R, 0.0f),
            filterPrefs.getFloat(FilterSettings.KEY_OUTLINE_COLOR_G, 0.0f),
            filterPrefs.getFloat(FilterSettings.KEY_OUTLINE_COLOR_B, 0.0f),
            1.0f
        )

        val root = FrameLayout(this)

        // Init Cubism
        LAppPal.setup(this)
        val option = CubismFramework.Option()
        option.logFunction = LAppPal.PrintLogFunction()
        option.loggingLevel = LAppDefine.cubismLoggingLevel
        CubismFramework.cleanUp()
        CubismFramework.startUp(option)
        LAppPal.updateTime()

        val textureManager = LAppTextureManager(this)
        live2DManager = LAppLive2DManager(textureManager)
        live2DManager?.setFitToScreen(true)
        live2DManager?.setModelScale(modelScale)
        live2DManager?.setModelOffset(offsetX, offsetY)

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

        // Controls panel
        val controlsPanel = buildControlsPanel()
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
        val pad = (12 * dp).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            setPadding(pad, pad, pad, (pad * 2))
        }

        // Saturation row
        val satRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val satSwitch = Switch(this).apply {
            text = "Saturation"
            setTextColor(Color.WHITE)
            isChecked = postProcess.isSaturationEnabled
            setOnCheckedChangeListener { _, checked ->
                postProcess.isSaturationEnabled = checked
            }
        }
        satRow.addView(satSwitch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val satLabel = TextView(this).apply {
            text = "%.1f".format(postProcess.saturationAmount)
            setTextColor(Color.WHITE)
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }

        val satSlider = SeekBar(this).apply {
            max = 300
            progress = (postProcess.saturationAmount * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress / 100f
                    postProcess.saturationAmount = value
                    satLabel.text = "%.1f".format(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        satRow.addView(satSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))
        satRow.addView(satLabel)
        panel.addView(satRow)

        // Outline row
        val outRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val outSwitch = Switch(this).apply {
            text = "Outline"
            setTextColor(Color.WHITE)
            isChecked = postProcess.isOutlineEnabled
            setOnCheckedChangeListener { _, checked ->
                postProcess.isOutlineEnabled = checked
            }
        }
        outRow.addView(outSwitch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val outLabel = TextView(this).apply {
            text = "%d".format(postProcess.outlineThickness.toInt())
            setTextColor(Color.WHITE)
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }

        val outSlider = SeekBar(this).apply {
            max = 100
            progress = postProcess.outlineThickness.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress.toFloat()
                    postProcess.outlineThickness = value
                    outLabel.text = "%d".format(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        outRow.addView(outSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))
        outRow.addView(outLabel)
        panel.addView(outRow)

        // Outline color row
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        val colorLabel = TextView(this).apply {
            text = "Outline Color:"
            setTextColor(Color.WHITE)
            setPadding(0, 0, (8 * dp).toInt(), 0)
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

        val btnSize = (32 * dp).toInt()
        val btnMargin = (4 * dp).toInt()

        for (preset in presets) {
            val colorBtn = Button(this).apply {
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.color)
                    setStroke((2 * dp).toInt(), Color.WHITE)
                }
                background = shape
                setOnClickListener {
                    postProcess.setOutlineColor(preset.r, preset.g, preset.b, 1.0f)
                }
            }
            val btnParams2 = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(btnMargin, 0, btnMargin, 0)
            }
            colorRow.addView(colorBtn, btnParams2)
        }

        panel.addView(colorRow)

        // Buttons row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        val btnReset = Button(this).apply {
            text = "Reset"
            setOnClickListener {
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
                satSlider.progress = 150
                outSlider.progress = 2
            }
        }
        btnRow.addView(btnReset)

        val btnApply = Button(this).apply {
            text = "Apply Wallpaper"
            setOnClickListener { applyWallpaper() }
        }
        btnRow.addView(btnApply)

        panel.addView(btnRow)
        return panel
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
                    true
                }
                else -> true
            }
        }
    }

    private fun applyWallpaper() {
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
            .apply()

        Log.d(TAG, "Settings saved: scale=$modelScale sat=${postProcess.isSaturationEnabled} outline=${postProcess.isOutlineEnabled}")

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
        live2DManager?.releaseModel()
        CubismFramework.dispose()
    }

    inner class SetupRenderer : GLSurfaceView.Renderer {

        private var modelLoaded = false
        private var bgTextureId = 0
        private var bgShaderProgram = 0
        private var bgPositionHandle = 0
        private var bgTexCoordHandle = 0
        private var bgTextureHandle = 0

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            CubismFramework.initialize()
            postProcess.init()

            loadBackground()
            initBgShader()
        }

        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            live2DManager?.setWindowSize(width, height)
            postProcess.resize(width, height)

            if (!modelLoaded) {
                live2DManager?.loadModel("Alice/", "Alice Cross Tensor.model3.json")
                modelLoaded = true
            }
        }

        override fun onDrawFrame(unused: GL10?) {
            LAppPal.updateTime()

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glClearDepthf(1.0f)

            // Draw background
            GLES20.glDisable(GLES20.GL_BLEND)
            drawBackground()

            // Draw model with optional post-processing
            val useFilters = postProcess.isAnyFilterEnabled
            if (useFilters) {
                postProcess.beginCapture()
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                live2DManager?.onUpdate()
                postProcess.endCaptureAndApply()
            } else {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                live2DManager?.onUpdate()
            }
        }

        private fun loadBackground() {
            try {
                val file = File(filesDir, "saved_wallpaper.png")
                if (!file.exists()) {
                    Log.d(TAG, "No background image")
                    return
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return

                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                bgTextureId = texIds[0]

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

                Log.d(TAG, "Background loaded: ${bitmap.width}x${bitmap.height}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load background", e)
            }
        }

        private fun initBgShader() {
            val vs = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """.trimIndent()

            val fs = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()

            val vertShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
            val fragShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)

            bgShaderProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(bgShaderProgram, vertShader)
            GLES20.glAttachShader(bgShaderProgram, fragShader)
            GLES20.glLinkProgram(bgShaderProgram)

            bgPositionHandle = GLES20.glGetAttribLocation(bgShaderProgram, "aPosition")
            bgTexCoordHandle = GLES20.glGetAttribLocation(bgShaderProgram, "aTexCoord")
            bgTextureHandle = GLES20.glGetUniformLocation(bgShaderProgram, "uTexture")
        }

        private fun compileShader(type: Int, code: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
            return shader
        }

        private fun drawBackground() {
            if (bgTextureId == 0) return

            val vertices = floatArrayOf(
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
            )

            val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buffer.put(vertices).position(0)

            GLES20.glUseProgram(bgShaderProgram)

            buffer.position(0)
            GLES20.glEnableVertexAttribArray(bgPositionHandle)
            GLES20.glVertexAttribPointer(bgPositionHandle, 2, GLES20.GL_FLOAT, false, 16, buffer)

            buffer.position(2)
            GLES20.glEnableVertexAttribArray(bgTexCoordHandle)
            GLES20.glVertexAttribPointer(bgTexCoordHandle, 2, GLES20.GL_FLOAT, false, 16, buffer)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(bgTextureHandle, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(bgPositionHandle)
            GLES20.glDisableVertexAttribArray(bgTexCoordHandle)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }
}
