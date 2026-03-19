package com.example.liveai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.liveai.live2d.CubismLifecycleManager
import com.example.liveai.live2d.Live2DRenderer
import com.example.liveai.live2d.Live2DSession
import com.example.liveai.live2d.Live2DSessionFactory

class OverlayService : Service() {

    companion object {
        private const val TAG = "LiveAI"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1
        private const val MIN_SIZE_DP = 100
        private const val MAX_SIZE_DP = 800
        private const val BORDER_FADE_DELAY_MS = 1000L

        @Volatile
        var isRunning = false
            private set

        private var pendingRestart: (() -> Unit)? = null

        /**
         * Safely restart the overlay service. If already running, waits for
         * onDestroy to complete before invoking [onReady] to start the new one.
         */
        fun requestRestart(context: Context, onReady: () -> Unit) {
            if (isRunning) {
                Log.d(TAG, "requestRestart: stopping existing service first")
                pendingRestart = onReady
                context.stopService(Intent(context, OverlayService::class.java))
            } else {
                onReady()
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var containerView: FrameLayout? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var borderView: View? = null
    private var live2DRenderer: Live2DRenderer? = null
    private var session: Live2DSession? = null
    private var overlayWidth = 0
    private var overlayHeight = 0
    private var modelAspect = 1.0f
    private var overlayParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideBorderRunnable = Runnable {
        borderView?.animate()?.alpha(0f)?.setDuration(300)?.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (live2DRenderer != null) {
            applyFilterSettings()
            Log.d(TAG, "Filters reloaded")
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        isRunning = true
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            CubismLifecycleManager.acquire(this)

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            addOverlayView()
            Log.d(TAG, "Overlay view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed in onCreate", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveAI Overlay")
            .setContentText("Overlay is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun addOverlayView() {
        val dp = resources.displayMetrics.density
        overlayWidth = (300 * dp).toInt()
        overlayHeight = (300 * dp).toInt()

        session = Live2DSessionFactory.create(this, testAudio = true) // TODO: set false for real mic

        live2DRenderer = Live2DRenderer(
            session!!.manager,
            "Alice/",
            "Alice Cross Tensor.model3.json"
        )

        live2DRenderer?.setOnModelLoadedListener { canvasWidth, canvasHeight ->
            modelAspect = canvasWidth / canvasHeight
            handler.post {
                val params = overlayParams ?: return@post
                overlayHeight = (overlayWidth / modelAspect).toInt()
                params.width = overlayWidth
                params.height = overlayHeight
                try {
                    windowManager.updateViewLayout(containerView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resize overlay", e)
                }
            }
        }

        applyFilterSettings()

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderOnTop(true)
            setRenderer(live2DRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        borderView = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke((2 * dp).toInt(), Color.argb(200, 100, 200, 255))
                setColor(Color.TRANSPARENT)
            }
            alpha = 0f
        }

        containerView = FrameLayout(this)
        containerView!!.addView(glSurfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        containerView!!.addView(borderView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        overlayParams = WindowManager.LayoutParams(
            overlayWidth,
            overlayHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        setupTouchHandlers(overlayParams!!)
        windowManager.addView(containerView, overlayParams)
    }

    private fun applyFilterSettings() {
        val prefs = getSharedPreferences(FilterSettings.PREFS_NAME, Context.MODE_PRIVATE)
        live2DRenderer?.postProcess?.isSaturationEnabled =
            prefs.getBoolean(FilterSettings.KEY_SATURATION, false)
        live2DRenderer?.postProcess?.isOutlineEnabled =
            prefs.getBoolean(FilterSettings.KEY_OUTLINE, false)
        live2DRenderer?.postProcess?.saturationAmount =
            prefs.getFloat(FilterSettings.KEY_SATURATION_AMOUNT, 1.5f)
        live2DRenderer?.postProcess?.outlineThickness =
            prefs.getFloat(FilterSettings.KEY_OUTLINE_THICKNESS, 1.5f)
        live2DRenderer?.postProcess?.setOutlineColor(
            prefs.getFloat(FilterSettings.KEY_OUTLINE_COLOR_R, 0.0f),
            prefs.getFloat(FilterSettings.KEY_OUTLINE_COLOR_G, 0.0f),
            prefs.getFloat(FilterSettings.KEY_OUTLINE_COLOR_B, 0.0f),
            1.0f
        )
    }

    private fun showBorder() {
        handler.removeCallbacks(hideBorderRunnable)
        borderView?.animate()?.cancel()
        borderView?.alpha = 1f
    }

    private fun scheduleBorderFade() {
        handler.removeCallbacks(hideBorderRunnable)
        handler.postDelayed(hideBorderRunnable, BORDER_FADE_DELAY_MS)
    }

    private fun setupTouchHandlers(params: WindowManager.LayoutParams) {
        val dp = resources.displayMetrics.density
        val minWidth = (MIN_SIZE_DP * dp).toInt()
        val maxWidth = (MAX_SIZE_DP * dp).toInt()

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isScaling = false

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    showBorder()
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    overlayWidth = (overlayWidth * detector.scaleFactor).toInt()
                        .coerceIn(minWidth, maxWidth)
                    overlayHeight = (overlayWidth / modelAspect).toInt()

                    val oldCenterX = params.x + params.width / 2
                    val oldCenterY = params.y + params.height / 2

                    params.width = overlayWidth
                    params.height = overlayHeight

                    params.x = oldCenterX - overlayWidth / 2
                    params.y = oldCenterY - overlayHeight / 2

                    windowManager.updateViewLayout(containerView, params)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                    scheduleBorderFade()
                }
            }
        )

        glSurfaceView?.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isScaling && event.pointerCount == 1) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(containerView, params)
                    }
                    true
                }
                else -> true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy")
        handler.removeCallbacks(hideBorderRunnable)

        containerView?.let {
            glSurfaceView?.onPause()
            windowManager.removeView(it)
        }
        containerView = null
        glSurfaceView = null
        borderView = null
        live2DRenderer = null

        session?.let { Live2DSessionFactory.destroy(it) }
        session = null

        CubismLifecycleManager.release()

        isRunning = false

        // If a restart was pending, invoke it on the main thread
        pendingRestart?.let { restart ->
            pendingRestart = null
            handler.post(restart)
        }
    }
}
