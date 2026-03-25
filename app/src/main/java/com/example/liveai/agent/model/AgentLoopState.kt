package com.example.liveai.agent.model

sealed interface AgentLoopState {
    data object Idle : AgentLoopState
    data object Queued : AgentLoopState

    data class Generating(
        val iteration: Int
    ) : AgentLoopState

    data class ExecutingTools(
        val iteration: Int,
        val toolCalls: List<ToolCallRequest>,
        val completedCount: Int
    ) : AgentLoopState

    data object Cancelled : AgentLoopState

    data class Error(
        val message: String,
        val recoverable: Boolean
    ) : AgentLoopState
}
