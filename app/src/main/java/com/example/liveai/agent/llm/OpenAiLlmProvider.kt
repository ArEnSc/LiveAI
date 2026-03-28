package com.example.liveai.agent.llm

import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as SdkChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.example.liveai.agent.model.FinishReason
import com.example.liveai.agent.model.LlmResponse
import com.example.liveai.agent.model.Message
import com.example.liveai.agent.model.ToolCallRequest
import com.example.liveai.agent.model.ToolDefinition
import com.example.liveai.agent.model.TokenUsage
import com.example.liveai.agent.AgentDebug
import kotlinx.coroutines.flow.collect

/**
 * LlmProvider implementation using the aallam OpenAI Kotlin SDK.
 * Works with any OpenAI-compatible API (OpenAI, Featherless, etc.)
 * by configuring the base URL.
 */
class OpenAiLlmProvider(
    apiKey: String,
    baseUrl: String = "https://api.openai.com/v1/",
    private val model: String = "gpt-4o",
    private val temperature: Double? = 0.7,
    private val maxTokens: Int? = 4096
) : LlmProvider {

    companion object {
        private const val TAG = "OpenAiLlmProvider"
    }

    private var client: OpenAI? = OpenAI(
        OpenAIConfig(
            token = apiKey,
            host = OpenAIHost(baseUrl = baseUrl)
        )
    )

    override fun release() {
        client?.close()
        client = null
    }

    override suspend fun generate(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        onChunk: ((String) -> Unit)?
    ): LlmResponse {
        val sdkMessages = messages.map { it.toSdkMessage() }
        val sdkTools = tools.map { it.toSdkTool() }.ifEmpty { null }

        val request = ChatCompletionRequest(
            model = ModelId(model),
            messages = sdkMessages,
            tools = sdkTools,
            temperature = temperature,
            maxTokens = maxTokens
        )

        return if (onChunk != null) {
            generateStreaming(request, onChunk)
        } else {
            generateBlocking(request)
        }
    }

    private suspend fun generateBlocking(request: ChatCompletionRequest): LlmResponse {
        val c = client ?: throw IllegalStateException("LlmProvider has been released")
        val completion = c.chatCompletion(request)
        val choice = completion.choices.firstOrNull()
        val message = choice?.message

        return LlmResponse(
            content = message?.content,
            toolCalls = message?.toolCalls?.mapNotNull { it.toToolCallRequest() } ?: emptyList(),
            finishReason = mapFinishReason(choice?.finishReason),
            usage = completion.usage?.let {
                TokenUsage(
                    promptTokens = it.promptTokens ?: 0,
                    completionTokens = it.completionTokens ?: 0
                )
            }
        )
    }

    private suspend fun generateStreaming(
        request: ChatCompletionRequest,
        onChunk: (String) -> Unit
    ): LlmResponse {
        val contentBuilder = StringBuilder()
        val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()
        var finishReason: FinishReason = FinishReason.COMPLETE
        var promptTokens = 0
        var completionTokens = 0
        val startMs = System.currentTimeMillis()
        var firstTokenMs = 0L
        var tokenCount = 0

        val c = client ?: throw IllegalStateException("LlmProvider has been released")
        AgentDebug.log(TAG) { "stream: request sent to $model (${request.messages.size} messages)" }

        c.chatCompletions(request).collect { chunk: ChatCompletionChunk ->
            val delta = chunk.choices.firstOrNull()?.delta

            // Stream text content
            val textPart = delta?.content
            if (textPart != null) {
                if (firstTokenMs == 0L) {
                    firstTokenMs = System.currentTimeMillis()
                    AgentDebug.log(TAG) { "stream: first token at +${firstTokenMs - startMs}ms" }
                }
                tokenCount++
                contentBuilder.append(textPart)
                onChunk(textPart)
            }

            // Accumulate tool calls from deltas
            delta?.toolCalls?.forEach { toolCallChunk ->
                val index = toolCallChunk.index ?: 0
                val builder = toolCallBuilders.getOrPut(index) { ToolCallBuilder() }
                toolCallChunk.id?.let { builder.id = it.id }
                toolCallChunk.function?.let { fn ->
                    fn.name?.let { builder.name = it }
                    fn.arguments?.let { builder.arguments.append(it) }
                }
            }

            // Capture finish reason
            chunk.choices.firstOrNull()?.finishReason?.let {
                finishReason = mapFinishReason(it)
            }

            // Capture usage if present
            chunk.usage?.let {
                promptTokens = it.promptTokens ?: 0
                completionTokens = it.completionTokens ?: 0
            }
        }

        val totalMs = System.currentTimeMillis() - startMs
        val streamMs = if (firstTokenMs > 0) System.currentTimeMillis() - firstTokenMs else 0
        val tps = if (streamMs > 0) tokenCount * 1000.0 / streamMs else 0.0
        AgentDebug.log(TAG) { "stream: done total=${totalMs}ms ttft=${firstTokenMs - startMs}ms tokens=$tokenCount stream=${streamMs}ms (${String.format("%.1f", tps)} tok/s)" }

        val toolCalls = toolCallBuilders.entries
            .sortedBy { it.key }
            .mapNotNull { (_, builder) -> builder.build() }

        val content = contentBuilder.toString().ifEmpty { null }

        return LlmResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = if (toolCalls.isNotEmpty() && finishReason == FinishReason.COMPLETE) {
                FinishReason.TOOL_CALLS
            } else {
                finishReason
            },
            usage = if (promptTokens > 0 || completionTokens > 0) {
                TokenUsage(promptTokens, completionTokens)
            } else {
                null
            }
        )
    }

    private fun mapFinishReason(reason: com.aallam.openai.api.core.FinishReason?): FinishReason {
        return when (reason?.value) {
            "stop" -> FinishReason.COMPLETE
            "tool_calls" -> FinishReason.TOOL_CALLS
            "length" -> FinishReason.LENGTH
            else -> FinishReason.COMPLETE
        }
    }

    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun build(): ToolCallRequest? {
            if (id.isEmpty() || name.isEmpty()) return null
            return ToolCallRequest(
                id = id,
                functionName = name,
                argumentsJson = arguments.toString()
            )
        }
    }
}

// --- Extension functions for translating between agent models and SDK types ---

internal fun Message.toSdkMessage(): SdkChatMessage = when (this) {
    is Message.System -> SdkChatMessage.System(content = content)
    is Message.User -> SdkChatMessage.User(content = content)
    is Message.Assistant -> {
        val sdkToolCalls = toolCalls.map { tc ->
            ToolCall.Function(
                id = ToolId(tc.id),
                function = com.aallam.openai.api.chat.FunctionCall(
                    nameOrNull = tc.functionName,
                    argumentsOrNull = tc.argumentsJson
                )
            )
        }.ifEmpty { null }
        SdkChatMessage.Assistant(
            content = content,
            toolCalls = sdkToolCalls
        )
    }
    is Message.ToolResult -> SdkChatMessage.Tool(
        content = content,
        toolCallId = ToolId(toolCallId)
    )
}

internal fun ToolDefinition.toSdkTool(): Tool = Tool.function(
    name = name,
    description = description,
    parameters = Parameters.fromJsonString(parametersSchema)
)

internal fun ToolCall.toToolCallRequest(): ToolCallRequest? {
    if (this is ToolCall.Function) {
        return ToolCallRequest(
            id = id.id,
            functionName = function.name,
            argumentsJson = function.arguments
        )
    }
    return null
}
