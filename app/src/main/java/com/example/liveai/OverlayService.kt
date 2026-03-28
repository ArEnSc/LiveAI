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
import com.example.liveai.agent.AgentDebug
import com.example.liveai.agent.AgentProviders
import com.example.liveai.agent.LlmProviderConfig
import com.example.liveai.agent.tts.PocketTtsProvider
import com.example.liveai.chat.ChatOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        private const val TAG = "LiveAI"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
            private set

        /** Wallpaper reads mouthVolume for lip sync (same process). */
        @Volatile
        var sharedTtsProvider: PocketTtsProvider? = null
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
    private var providers: AgentProviders? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        isRunning = true
        AgentDebug.enabled = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val config = loadLlmConfig()
            val agents = AgentProviders.create(this, config)
            providers = agents
            sharedTtsProvider = agents.ttsProvider

            serviceScope.launch { agents.warmUp() }

            val systemPrompt = assets.open("system_prompt_v1.txt")
                .bufferedReader().use { it.readText() }

            chatOverlayManager = ChatOverlayManager(
                this, windowManager,
                agents.llmProvider, agents.ttsProvider, systemPrompt
            ).apply {
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy")

        chatOverlayManager?.destroy()
        chatOverlayManager = null

        providers?.release()
        providers = null
        sharedTtsProvider = null

        isRunning = false

        pendingRestart?.let { restart ->
            pendingRestart = null
            handler.post(restart)
        }
    }

    private fun loadLlmConfig(): LlmProviderConfig {
        // TODO: load from SharedPreferences / settings UI (Stage 17)
        return LlmProviderConfig(
            apiKey = "rc_b96a713b7ea1fe421175cebd91312652542ab925070da7800737fd23bfe19cd5",
            baseUrl = "https://api.featherless.ai/v1/",
            model = "huihui-ai/Meta-Llama-3.1-8B-Instruct-abliterated"
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Overlay Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveAI Overlay")
            .setContentText("Chat overlay is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
