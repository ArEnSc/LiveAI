package com.example.liveai.agent.model

import java.util.UUID

/**
 * Universal message format used across the agent system.
 * All providers translate to/from these types.
 */
sealed interface Message {
    val id: String
    val timestamp: Long

    data class System(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String
    ) : Message

    data class User(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String,
        val source: InputSource = InputSource.KEYBOARD,
        val imageUri: String? = null
    ) : Message

    data class Assistant(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String? = null,
        val toolCalls: List<ToolCallRequest> = emptyList(),
        val isStreaming: Boolean = false
    ) : Message

    data class ToolResult(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val toolCallId: String,
        val toolName: String,
        val content: String,
        val isError: Boolean = false
    ) : Message
}

enum class InputSource {
    KEYBOARD,
    VOICE,
    SYSTEM
}
