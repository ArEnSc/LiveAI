package com.example.liveai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.example.liveai.chat.ChatOverlayManager

class OverlayService : Service() {

    companion object {
        private const val TAG = "LiveAI"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
            private set

        private var pendingRestart: (() -> Unit)? = null

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
    private var chatOverlayManager: ChatOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        isRunning = true
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            chatOverlayManager = ChatOverlayManager(this, windowManager).apply {
                create()
                resume()
                showTab()
            }
            Log.d(TAG, "Chat overlay tab shown")
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
            .setContentText("Chat overlay is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy")

        chatOverlayManager?.destroy()
        chatOverlayManager = null

        isRunning = false

        pendingRestart?.let { restart ->
            pendingRestart = null
            handler.post(restart)
        }
    }
}
