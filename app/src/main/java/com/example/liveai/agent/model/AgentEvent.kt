package com.example.liveai.agent.model

sealed interface AgentEvent {

    data class ResponseChunk(
        val text: String
    ) : AgentEvent

    data class ToolCallStarted(
        val toolCallId: String,
        val toolName: String,
        val argsJson: String
    ) : AgentEvent

    data class ToolCallCompleted(
        val toolCallId: String,
        val toolName: String,
        val result: String,
        val durationMs: Long,
        val isError: Boolean
    ) : AgentEvent

    data class Complete(
        val fullResponse: String,
        val totalIterations: Int,
        val usage: TokenUsage?
    ) : AgentEvent

    data class Error(
        val message: String,
        val recoverable: Boolean
    ) : AgentEvent
}
