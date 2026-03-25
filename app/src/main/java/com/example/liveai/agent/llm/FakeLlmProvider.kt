package com.example.liveai.agent.llm

import com.example.liveai.agent.model.LlmResponse
import com.example.liveai.agent.model.Message
import com.example.liveai.agent.model.ToolDefinition
import kotlinx.coroutines.delay

/**
 * Fake LLM provider that returns scripted responses in sequence.
 * Use in tests to control exactly what the "LLM" says.
 */
class FakeLlmProvider(
    private val responses: List<LlmResponse>,
    private val delayMs: Long = 0L
) : LlmProvider {

    private var callIndex = 0

    /** How many times generate() has been called. */
    val generateCallCount: Int get() = callIndex

    /** Messages received on each call. */
    val receivedMessages: MutableList<List<Message>> = mutableListOf()

    /** Tools received on each call. */
    val receivedTools: MutableList<List<ToolDefinition>> = mutableListOf()

    override suspend fun generate(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        onChunk: ((String) -> Unit)?
    ): LlmResponse {
        receivedMessages.add(messages.toList())
        receivedTools.add(tools.toList())

        if (callIndex >= responses.size) {
            throw IllegalStateException(
                "FakeLlmProvider exhausted: called ${callIndex + 1} times but only ${responses.size} responses scripted"
            )
        }

        if (delayMs > 0) {
            delay(delayMs)
        }

        val response = responses[callIndex]
        callIndex++

        // Simulate streaming if callback provided and response has content
        if (onChunk != null && response.content != null) {
            for (char in response.content) {
                onChunk(char.toString())
            }
        }

        return response
    }

    /** Reset call tracking for reuse. */
    fun reset() {
        callIndex = 0
        receivedMessages.clear()
        receivedTools.clear()
    }
}
