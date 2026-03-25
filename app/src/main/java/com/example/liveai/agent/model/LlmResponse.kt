package com.example.liveai.agent.model

data class LlmResponse(
    val content: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val finishReason: FinishReason = FinishReason.COMPLETE,
    val usage: TokenUsage? = null
)

enum class FinishReason {
    COMPLETE,
    TOOL_CALLS,
    LENGTH,
    ERROR
}

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int
)
