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
import com.example.liveai.live2d.CubismLifecycleManager
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppPal
import com.example.liveai.live2d.Live2DSession
import com.example.liveai.live2d.Live2DSessionFactory
import com.example.liveai.live2d.PostProcessFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Live2DWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "LiveAI-Wallpaper"
    }

    override fun onCreateEngine(): Engine {
        Log.d(TAG, "===== onCreateEngine =====")
        return Live2DEngine()
    }

    inner class Live2DEngine : Engine() {

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var eglConfig: EGLConfig? = null

        private var live2DManager: LAppLive2DManager? = null
        private val postProcess = PostProcessFilter()
        private var session: Live2DSession? = null
        private var modelLoaded = false
        private var glReady = false
        private var cubismAcquired = false
        private var cubismGeneration = 0L
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private var bgTextureId = 0
        private var bgShaderProgram = 0
        private var bgPositionHandle = 0
        private var bgTexCoordHandle = 0
        private var bgTextureHandle = 0

        // Loading spinner GL state
        private var loadingShaderProgram = 0
        private var loadingPositionHandle = 0
        private var loadingResolutionHandle = 0
        private var loadingTimeHandle = 0
        private var loadingStartTime = 0L

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var frameCount = 0L
        private val engineId = System.identityHashCode(this)

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (visible) {
                    drawFrame()
                    handler.postDelayed(this, 16L)
                }
            }
        }

        private fun logState(event: String) {
            Log.d(TAG, "[$engineId] $event | " +
                "glReady=$glReady modelLoaded=$modelLoaded visible=$visible " +
                "isVisible=$isVisible cubismAcq=$cubismAcquired " +
                "eglOk=${eglSurface != EGL14.EGL_NO_SURFACE} " +
                "session=${session != null} manager=${live2DManager != null} " +
                "surface=${surfaceWidth}x${surfaceHeight} frames=$frameCount")
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            logState("onCreate")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            logState("onSurfaceCreated BEFORE setup")
            setupEverything(holder)
            logState("onSurfaceCreated AFTER setup")
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            logState("onSurfaceChanged ${width}x${height}")

            // If onSurfaceCreated wasn't called (engine reuse), set up now
            if (!glReady) {
                Log.w(TAG, "[$engineId] GL not ready in onSurfaceChanged — initializing now")
                setupEverything(holder)
                logState("onSurfaceChanged AFTER late setup")
            }

            if (!makeCurrent()) {
                Log.e(TAG, "[$engineId] makeCurrent failed in onSurfaceChanged")
                return
            }

            GLES20.glViewport(0, 0, width, height)
            live2DManager?.setWindowSize(width, height)
            postProcess.resize(width, height)

            if (!modelLoaded) {
                loadModel()
                logState("onSurfaceChanged AFTER loadModel")
            }

            drawFrame()
            ensureDrawLoop()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            logState("onVisibilityChanged($visible)")
            if (visible) {
                // If another consumer (e.g. SetupActivity) took over Cubism,
                // our GL resources are stale. Re-setup everything on our EGL
                // context so the wallpaper resumes rendering.
                if (CubismLifecycleManager.generation != cubismGeneration) {
                    Log.d(TAG, "[$engineId] Generation stale (ours=$cubismGeneration, current=${CubismLifecycleManager.generation}) — re-initializing")
                    setupEverything(surfaceHolder)
                    if (glReady && surfaceWidth > 0 && surfaceHeight > 0 && makeCurrent()) {
                        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
                        live2DManager?.setWindowSize(surfaceWidth, surfaceHeight)
                        postProcess.resize(surfaceWidth, surfaceHeight)
                        if (!modelLoaded) {
                            loadModel()
                        }
                    }
                }
                ensureDrawLoop()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            logState("onSurfaceDestroyed BEFORE tearDown")
            visible = false
            handler.removeCallbacks(drawRunnable)
            tearDown()
            logState("onSurfaceDestroyed AFTER tearDown")
        }

        override fun onDestroy() {
            super.onDestroy()
            logState("onDestroy")
            handler.removeCallbacks(drawRunnable)
        }

        private fun setupEverything(holder: SurfaceHolder?) {
            Log.d(TAG, "[$engineId] setupEverything START holder=$holder")
            if (holder == null) {
                Log.e(TAG, "[$engineId] setupEverything: holder is null — ABORTING")
                return
            }

            // Tear down previous state if any (handles engine reuse)
            tearDown()

            try {
                initEGL(holder)
                Log.d(TAG, "[$engineId] EGL init done, eglSurface=${eglSurface != EGL14.EGL_NO_SURFACE}")
            } catch (e: Exception) {
                Log.e(TAG, "[$engineId] EGL init EXCEPTION", e)
                return
            }

            if (!makeCurrent()) {
                Log.e(TAG, "[$engineId] makeCurrent FAILED after EGL init — ABORTING")
                return
            }
            Log.d(TAG, "[$engineId] makeCurrent OK after EGL init")

            try {
                // Force full framework reinit — each wallpaper engine has its own
                // EGL context, so Cubism GL resources must be recreated here.
                Log.d(TAG, "[$engineId] Force-reinitializing CubismFramework for new EGL context...")
                cubismGeneration = CubismLifecycleManager.forceReinitialize(this@Live2DWallpaperService)
                cubismAcquired = true
                Log.d(TAG, "[$engineId] CubismFramework reinitialized (gen=$cubismGeneration)")

                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                Log.d(TAG, "[$engineId] Creating Live2DSession...")
                session = Live2DSessionFactory.create(this@Live2DWallpaperService)
                live2DManager = session?.manager
                live2DManager?.setFitToScreen(true)
                Log.d(TAG, "[$engineId] Session created, manager=${live2DManager != null}")

                // Load saved position/scale settings
                val prefs = getSharedPreferences(WallpaperSetupActivity.PREFS_NAME, MODE_PRIVATE)
                val scale = prefs.getFloat(WallpaperSetupActivity.KEY_SCALE, 1.0f)
                val offX = prefs.getFloat(WallpaperSetupActivity.KEY_OFFSET_X, 0.0f)
                val offY = prefs.getFloat(WallpaperSetupActivity.KEY_OFFSET_Y, 0.0f)
                live2DManager?.setModelScale(scale)
                live2DManager?.setModelOffset(offX, offY)
                Log.d(TAG, "[$engineId] Settings: scale=$scale offX=$offX offY=$offY")

                // Load filter settings
                val filterPrefs = getSharedPreferences(FilterSettings.PREFS_NAME, MODE_PRIVATE)
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

                LAppPal.updateTime()
                postProcess.init()
                Log.d(TAG, "[$engineId] PostProcess initialized")

                loadBackgroundWallpaper()
                initBgShader()
                initLoadingShader()
                loadingStartTime = System.nanoTime()

                glReady = true
                Log.d(TAG, "[$engineId] setupEverything COMPLETE — glReady=true")
            } catch (e: Exception) {
                Log.e(TAG, "[$engineId] setupEverything EXCEPTION", e)
            }
        }

        private fun tearDown() {
            Log.d(TAG, "[$engineId] tearDown START session=${session != null} cubismAcq=$cubismAcquired")
            if (session != null) {
                try {
                    session?.let { Live2DSessionFactory.destroy(it) }
                } catch (e: Exception) {
                    // Framework may have been torn down by another consumer's
                    // forceReinitialize — releaseModel can NPE on stale state.
                    Log.w(TAG, "[$engineId] tearDown: session destroy failed (framework stale?)", e)
                }
                session = null
            }
            live2DManager = null
            modelLoaded = false
            glReady = false

            if (cubismAcquired) {
                try {
                    CubismLifecycleManager.release()
                } catch (e: Exception) {
                    Log.w(TAG, "[$engineId] tearDown: release failed", e)
                }
                cubismAcquired = false
            }

            destroyEGL()
            Log.d(TAG, "[$engineId] tearDown COMPLETE")
        }

        private fun loadModel() {
            if (live2DManager == null) {
                Log.e(TAG, "[$engineId] loadModel: manager is NULL — cannot load")
                return
            }

            Log.d(TAG, "[$engineId] loadModel: calling loadModel(\"Alice/\", \"Alice Cross Tensor.model3.json\")...")
            live2DManager?.loadModel("Alice/", "Alice Cross Tensor.model3.json")

            // Validate the model actually loaded
            val cw = live2DManager?.getCanvasWidth() ?: 0f
            val ch = live2DManager?.getCanvasHeight() ?: 0f
            if (cw > 0f && ch > 0f) {
                modelLoaded = true
                Log.d(TAG, "[$engineId] loadModel: SUCCESS canvas=${cw}x${ch}")
            } else {
                Log.e(TAG, "[$engineId] loadModel: FAILED canvas=${cw}x${ch} — model did not load")
                modelLoaded = false
            }
        }

        private fun ensureDrawLoop() {
            // Use either our cached visible OR the system's isVisible — don't
            // let a stale isVisible=false override a fresh onVisibilityChanged(true)
            val shouldRun = visible || isVisible
            Log.d(TAG, "[$engineId] ensureDrawLoop: visible=$visible isVisible=$isVisible shouldRun=$shouldRun glReady=$glReady")
            if (shouldRun) {
                visible = true
                handler.removeCallbacks(drawRunnable)
                handler.post(drawRunnable)
            }
        }

        private fun drawFrame() {
            if (!glReady || eglSurface == EGL14.EGL_NO_SURFACE) {
                if (frameCount == 0L) {
                    Log.w(TAG, "[$engineId] drawFrame SKIPPED: glReady=$glReady eglOk=${eglSurface != EGL14.EGL_NO_SURFACE}")
                }
                return
            }
            // Another consumer (e.g. SetupActivity) took over the Cubism framework.
            // Our GL resources are stale — stop drawing to avoid corrupting their state.
            if (CubismLifecycleManager.generation != cubismGeneration) {
                if (frameCount % 300 == 0L || frameCount <= 5) {
                    Log.w(TAG, "[$engineId] drawFrame SKIPPED: Cubism generation changed (ours=$cubismGeneration, current=${CubismLifecycleManager.generation})")
                }
                return
            }
            if (!makeCurrent()) {
                Log.e(TAG, "[$engineId] drawFrame: makeCurrent FAILED at frame $frameCount")
                return
            }

            frameCount++
            // Log first 5 frames, then every 300th (~5 seconds)
            if (frameCount <= 5 || frameCount % 300 == 0L) {
                Log.d(TAG, "[$engineId] drawFrame #$frameCount modelLoaded=$modelLoaded surface=${surfaceWidth}x${surfaceHeight}")
            }

            LAppPal.updateTime()

            GLES20.glClearColor(0.12f, 0.12f, 0.12f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glClearDepthf(1.0f)

            if (!modelLoaded) {
                drawLoadingSpinner()
            } else {
                // Draw the user's original wallpaper as background
                GLES20.glDisable(GLES20.GL_BLEND)
                drawBackground()

                // Hold the read lock while calling onUpdate so that
                // forceReinitialize (which takes the write lock) blocks
                // until this frame finishes — preventing shader corruption.
                // Hold the read lock while calling onUpdate so that
                // forceReinitialize (which takes the write lock) blocks
                // until this frame finishes — preventing shader corruption.
                val readLock = CubismLifecycleManager.frameworkLock.readLock()
                if (!readLock.tryLock()) return  // forceReinitialize in progress, skip frame

                try {
                    // Re-check generation inside the lock
                    if (CubismLifecycleManager.generation != cubismGeneration) return

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
                } finally {
                    readLock.unlock()
                }
            }

            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                val err = EGL14.eglGetError()
                Log.e(TAG, "[$engineId] eglSwapBuffers FAILED: 0x${Integer.toHexString(err)} at frame $frameCount")
                if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
                    Log.e(TAG, "[$engineId] Surface lost — marking glReady=false")
                    glReady = false
                }
            }
        }

        // --- Background ---

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

        // --- Loading spinner ---

        private fun initLoadingShader() {
            val vs = """
                attribute vec4 aPosition;
                void main() {
                    gl_Position = aPosition;
                }
            """.trimIndent()

            val fs = """
                precision mediump float;
                uniform vec2 uResolution;
                uniform float uTime;
                void main() {
                    vec2 uv = (gl_FragCoord.xy - uResolution * 0.5) / min(uResolution.x, uResolution.y);
                    float dist = length(uv);
                    float angle = atan(uv.y, uv.x);
                    float radius = 0.06;
                    float thickness = 0.012;
                    float ring = smoothstep(thickness, thickness * 0.5, abs(dist - radius));
                    float arc = smoothstep(0.0, 3.14159, mod(angle - uTime * 3.0, 6.28318));
                    float alpha = ring * arc * (0.6 + 0.4 * sin(uTime * 2.0));
                    gl_FragColor = vec4(0.7, 0.85, 1.0, alpha);
                }
            """.trimIndent()

            val vertShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
            val fragShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)

            loadingShaderProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(loadingShaderProgram, vertShader)
            GLES20.glAttachShader(loadingShaderProgram, fragShader)
            GLES20.glLinkProgram(loadingShaderProgram)

            loadingPositionHandle = GLES20.glGetAttribLocation(loadingShaderProgram, "aPosition")
            loadingResolutionHandle = GLES20.glGetUniformLocation(loadingShaderProgram, "uResolution")
            loadingTimeHandle = GLES20.glGetUniformLocation(loadingShaderProgram, "uTime")
        }

        private fun drawLoadingSpinner() {
            if (surfaceWidth == 0 || surfaceHeight == 0) return

            val elapsed = (System.nanoTime() - loadingStartTime) / 1_000_000_000f

            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )

            val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buffer.put(vertices).position(0)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glUseProgram(loadingShaderProgram)

            GLES20.glEnableVertexAttribArray(loadingPositionHandle)
            GLES20.glVertexAttribPointer(loadingPositionHandle, 2, GLES20.GL_FLOAT, false, 0, buffer)

            GLES20.glUniform2f(loadingResolutionHandle, surfaceWidth.toFloat(), surfaceHeight.toFloat())
            GLES20.glUniform1f(loadingTimeHandle, elapsed)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(loadingPositionHandle)
        }

        // --- EGL ---

        private fun initEGL(holder: SurfaceHolder) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.e(TAG, "eglGetDisplay failed")
                return
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                Log.e(TAG, "eglInitialize failed")
                return
            }

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

            if (eglConfig == null) {
                Log.e(TAG, "eglChooseConfig failed")
                return
            }

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )

            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.e(TAG, "eglCreateContext failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                return
            }

            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, eglConfig, holder.surface, intArrayOf(EGL14.EGL_NONE), 0
            )

            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                return
            }

            makeCurrent()
            Log.d(TAG, "EGL initialized successfully")
        }

        private fun makeCurrent(): Boolean {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return false
            val ok = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            if (!ok) {
                Log.e(TAG, "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }
            return ok
        }

        private fun destroyEGL() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
            }
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
