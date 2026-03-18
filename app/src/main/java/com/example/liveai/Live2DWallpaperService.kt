package com.example.liveai

import android.graphics.BitmapFactory
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.example.liveai.live2d.LAppDefine
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppPal
import com.example.liveai.live2d.LAppTextureManager
import com.live2d.sdk.cubism.framework.CubismFramework
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Live2DWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "LiveAI-Wallpaper"
    }

    override fun onCreateEngine(): Engine {
        return Live2DEngine()
    }

    inner class Live2DEngine : Engine() {

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var eglConfig: EGLConfig? = null

        private var live2DManager: LAppLive2DManager? = null
        private var cubismInitialized = false
        private var modelLoaded = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private var bgTextureId = 0
        private var bgShaderProgram = 0
        private var bgPositionHandle = 0
        private var bgTexCoordHandle = 0
        private var bgTextureHandle = 0

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (visible) {
                    drawFrame()
                    handler.postDelayed(this, 16L) // ~60fps
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.d(TAG, "Engine onCreate")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "onSurfaceCreated")
            initEGL(holder)
            initCubism()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.d(TAG, "onSurfaceChanged ${width}x${height}")
            surfaceWidth = width
            surfaceHeight = height

            makeCurrent()
            GLES20.glViewport(0, 0, width, height)
            live2DManager?.setWindowSize(width, height)

            if (!modelLoaded) {
                live2DManager?.loadModel("Alice/", "Alice Cross Tensor.model3.json")
                modelLoaded = true
                Log.d(TAG, "Model loaded")
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if (visible) {
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Log.d(TAG, "onSurfaceDestroyed")
            visible = false
            handler.removeCallbacks(drawRunnable)

            live2DManager?.releaseModel()
            live2DManager = null
            modelLoaded = false

            if (cubismInitialized) {
                CubismFramework.dispose()
                cubismInitialized = false
            }

            destroyEGL()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
        }

        private fun initCubism() {
            LAppPal.setup(this@Live2DWallpaperService)

            val option = CubismFramework.Option()
            option.logFunction = LAppPal.PrintLogFunction()
            option.loggingLevel = LAppDefine.cubismLoggingLevel

            CubismFramework.cleanUp()
            CubismFramework.startUp(option)

            makeCurrent()

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            CubismFramework.initialize()
            cubismInitialized = true

            val textureManager = LAppTextureManager(this@Live2DWallpaperService)
            live2DManager = LAppLive2DManager(textureManager)
            live2DManager?.setFitToScreen(true)

            // Load saved position/scale settings
            val prefs = getSharedPreferences(WallpaperSetupActivity.PREFS_NAME, MODE_PRIVATE)
            val scale = prefs.getFloat(WallpaperSetupActivity.KEY_SCALE, 1.0f)
            val offX = prefs.getFloat(WallpaperSetupActivity.KEY_OFFSET_X, 0.0f)
            val offY = prefs.getFloat(WallpaperSetupActivity.KEY_OFFSET_Y, 0.0f)
            live2DManager?.setModelScale(scale)
            live2DManager?.setModelOffset(offX, offY)
            Log.d(TAG, "Loaded settings: scale=$scale offsetX=$offX offsetY=$offY")

            LAppPal.updateTime()

            loadBackgroundWallpaper()
            initBgShader()
        }

        private fun loadBackgroundWallpaper() {
            try {
                val file = java.io.File(this@Live2DWallpaperService.filesDir, "saved_wallpaper.png")
                if (!file.exists()) {
                    Log.d(TAG, "No saved wallpaper found")
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

                Log.d(TAG, "Background wallpaper loaded: ${bitmap.width}x${bitmap.height}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load wallpaper", e)
            }
        }

        private fun initBgShader() {
            val vertexShader = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """.trimIndent()

            val fragmentShader = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()

            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)

            bgShaderProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(bgShaderProgram, vs)
            GLES20.glAttachShader(bgShaderProgram, fs)
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

        private fun drawFrame() {
            if (eglSurface == EGL14.EGL_NO_SURFACE) return
            makeCurrent()

            LAppPal.updateTime()

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glClearDepthf(1.0f)

            // Draw the user's original wallpaper as background
            GLES20.glDisable(GLES20.GL_BLEND)
            drawBackground()

            // Draw Live2D model on top with blending
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            live2DManager?.onUpdate()

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun initEGL(holder: SurfaceHolder?) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            eglConfig = configs[0]

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )

            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, eglConfig, holder!!.surface, intArrayOf(EGL14.EGL_NONE), 0
            )

            makeCurrent()
            Log.d(TAG, "EGL initialized")
        }

        private fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun destroyEGL() {
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
            Log.d(TAG, "EGL destroyed")
        }
    }
}
