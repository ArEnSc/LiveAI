package com.example.liveai.agent

import android.content.Context
import android.util.Log
import com.example.liveai.agent.llm.LlmProvider
import com.example.liveai.agent.llm.OpenAiLlmProvider
import com.example.liveai.agent.tts.PocketTtsProvider
import com.example.liveai.agent.tts.TtsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Configuration for the LLM backend. Kept separate from provider
 * creation so it can later come from SharedPreferences / settings UI.
 */
data class LlmProviderConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.openai.com/v1/",
    val model: String = "gpt-4o",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096
)

/**
 * Holds the live provider instances used by the overlay and wallpaper.
 *
 * Created once per overlay session, torn down when the service stops.
 * The [ttsProvider] is exposed for lip-sync reads from the wallpaper
 * render thread (same process).
 */
class AgentProviders(
    val llmProvider: LlmProvider,
    val ttsProvider: PocketTtsProvider
) {
    /** Pre-load TTS models so the first response speaks immediately. */
    suspend fun warmUp() {
        val ms = withContext(Dispatchers.IO) { ttsProvider.initialize() }
        Log.d(TAG, "TTS model loaded in ${ms}ms")
    }

    fun release() {
        ttsProvider.release()
        llmProvider.release()
    }

    companion object {
        private const val TAG = "AgentProviders"

        fun create(context: Context, config: LlmProviderConfig): AgentProviders {
            val llm = OpenAiLlmProvider(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens
            )
            val tts = PocketTtsProvider(context)
            return AgentProviders(llm, tts)
        }
    }
}
