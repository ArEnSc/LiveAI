package com.example.liveai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.example.liveai.live2d.LAppDefine
import com.example.liveai.live2d.LAppLive2DManager
import com.example.liveai.live2d.LAppPal
import com.example.liveai.live2d.LAppTextureManager
import com.example.liveai.live2d.Live2DRenderer
import com.live2d.sdk.cubism.framework.CubismFramework

class OverlayService : Service() {

    companion object {
        private const val TAG = "LiveAI"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private var glSurfaceView: GLSurfaceView? = null
    private var live2DManager: LAppLive2DManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            initCubism()

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            addOverlayView()
            Log.d(TAG, "Overlay view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed in onCreate", e)
        }
    }

    private fun initCubism() {
        LAppPal.setup(this)

        val option = CubismFramework.Option()
        option.logFunction = LAppPal.PrintLogFunction()
        option.loggingLevel = LAppDefine.cubismLoggingLevel

        CubismFramework.cleanUp()
        CubismFramework.startUp(option)

        LAppPal.updateTime()
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
        val sizePx = (300 * resources.displayMetrics.density).toInt()

        val textureManager = LAppTextureManager(this)
        live2DManager = LAppLive2DManager(textureManager)

        val renderer = Live2DRenderer(
            live2DManager!!,
            "Alice/",
            "Alice Cross Tensor.model3.json"
        )

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderOnTop(true)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        setupDragListener(params)
        windowManager.addView(glSurfaceView, params)
    }

    private fun setupDragListener(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        glSurfaceView?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(glSurfaceView, params)
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
        glSurfaceView?.let {
            it.onPause()
            windowManager.removeView(it)
        }
        live2DManager?.releaseModel()
        CubismFramework.dispose()
    }
}
