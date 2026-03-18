package com.example.liveai

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.liveai.live2d.LAppDefine
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppPal
import com.example.liveai.live2d.LAppTextureManager
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

        val btnApply = Button(this).apply {
            text = "Apply Wallpaper"
            setOnClickListener { applyWallpaper() }
        }
        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = (48 * resources.displayMetrics.density).toInt()
        }
        root.addView(btnApply, btnParams)

        val btnReset = Button(this).apply {
            text = "Reset"
            setOnClickListener {
                modelScale = 1.0f
                offsetX = 0.0f
                offsetY = 0.0f
                live2DManager?.setModelScale(modelScale)
                live2DManager?.setModelOffset(offsetX, offsetY)
            }
        }
        val resetParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            bottomMargin = (48 * resources.displayMetrics.density).toInt()
            leftMargin = (16 * resources.displayMetrics.density).toInt()
        }
        root.addView(btnReset, resetParams)

        setContentView(root)
        setupTouchHandling()
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
                            // Convert pixel delta to GL coordinates (-1..1)
                            offsetX += (dx / view.width) * 2.0f
                            offsetY -= (dy / view.height) * 2.0f
                            live2DManager?.setModelOffset(offsetX, offsetY)

                            lastTouchX = event.getX(pointerIndex)
                            lastTouchY = event.getY(pointerIndex)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    true
                }
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
        // Save settings
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_SCALE, modelScale)
            .putFloat(KEY_OFFSET_X, offsetX)
            .putFloat(KEY_OFFSET_Y, offsetY)
            .apply()

        Log.d(TAG, "Settings saved: scale=$modelScale offsetX=$offsetX offsetY=$offsetY")

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

            loadBackground()
            initBgShader()
        }

        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            live2DManager?.setWindowSize(width, height)

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

            GLES20.glDisable(GLES20.GL_BLEND)
            drawBackground()

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            live2DManager?.onUpdate()
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
