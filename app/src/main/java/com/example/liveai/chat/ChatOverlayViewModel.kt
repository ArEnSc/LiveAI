package com.example.liveai.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false
)

/**
 * Manages chat state and mock-streams responses.
 * Scoped to the ChatOverlayManager lifecycle (not a ViewModel subclass
 * since we're running in a Service, not an Activity).
 */
class ChatOverlayViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var streamJob: Job? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onSend(message: String) {
        // Cancel any in-progress stream
        streamJob?.cancel()

        // Add user message
        _uiState.update { state ->
            state.copy(
                messages = state.messages
                    .map { it.copy(isStreaming = false) } + ChatMessage(
                    text = message,
                    isUser = true
                )
            )
        }

        // Start mock streaming response
        streamJob = scope.launch {
            val response = generateMockResponse(message)
            streamResponse(response)
        }
    }

    private suspend fun streamResponse(fullText: String) {
        // Add empty assistant message in streaming state
        _uiState.update { state ->
            state.copy(
                messages = state.messages + ChatMessage(
                    text = "",
                    isUser = false,
                    isStreaming = true
                ),
                isStreaming = true
            )
        }

        // Stream character by character
        val builder = StringBuilder()
        for (char in fullText) {
            builder.append(char)
            val streamedText = builder.toString()

            _uiState.update { state ->
                val updated = state.messages.toMutableList()
                updated[updated.lastIndex] = ChatMessage(
                    text = streamedText,
                    isUser = false,
                    isStreaming = true
                )
                state.copy(messages = updated)
            }

            // Variable delay for natural feel — faster for spaces, slower for punctuation
            val delayMs = when (char) {
                ' ' -> 20L
                '.', '!', '?' -> 80L
                ',' -> 50L
                else -> 30L
            }
            delay(delayMs)
        }

        // Mark streaming complete
        _uiState.update { state ->
            val updated = state.messages.toMutableList()
            updated[updated.lastIndex] = updated.last().copy(isStreaming = false)
            state.copy(messages = updated, isStreaming = false)
        }
    }

    private fun generateMockResponse(userMessage: String): String {
        val lower = userMessage.lowercase()
        return when {
            "hello" in lower || "hi" in lower ->
                "Hey there! I'm your AI assistant. How can I help you today?"
            "weather" in lower ->
                "I don't have access to live weather data yet, but I can help with other things!"
            "help" in lower ->
                "I can answer questions, help with tasks, or just chat. What's on your mind?"
            else ->
                "You said: \"$userMessage\" — that's interesting! This is a mock response " +
                    "to demonstrate streaming. Real AI integration is coming soon."
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
