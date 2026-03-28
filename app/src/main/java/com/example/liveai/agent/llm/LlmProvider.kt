package com.example.liveai.agent.llm

import com.example.liveai.agent.model.LlmResponse
import com.example.liveai.agent.model.Message
import com.example.liveai.agent.model.ToolDefinition

/**
 * Interface for LLM API calls. Implementations handle provider-specific
 * serialization, retry, and streaming.
 */
interface LlmProvider {

    /**
     * Generate a response from the LLM.
     *
     * @param messages Conversation history (system, user, assistant, tool results)
     * @param tools Available tool definitions the LLM can call
     * @param onChunk Optional callback for streaming — receives incremental text tokens
     * @return Complete response with content and/or tool calls
     */
    suspend fun generate(
        messages: List<Message>,
        tools: List<ToolDefinition> = emptyList(),
        onChunk: ((String) -> Unit)? = null
    ): LlmResponse

    /** Release underlying resources (HTTP clients, connection pools). */
    fun release() {}
}
