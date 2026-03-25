package com.example.liveai.agent.model

data class LlmConfig(
    val provider: String,
    val model: String,
    val apiKey: String,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val systemPrompt: String = ""
)

data class TtsConfig(
    val provider: String,
    val voice: String? = null,
    val speed: Float = 1.0f,
    val apiKey: String? = null
)
