package com.example.liveai.chat

import android.util.Log
import com.example.liveai.agent.AgentDebug
import com.example.liveai.agent.llm.LlmProvider
import com.example.liveai.agent.model.Message
import com.example.liveai.agent.tts.TtsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatMode { Short, History }

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val mode: ChatMode = ChatMode.Short,
    val speechState: SpeechState = SpeechState.Idle
) {
    val visibleMessages: List<ChatMessage>
        get() {
            if (mode == ChatMode.History || messages.isEmpty()) return messages
            val lastUserIndex = messages.indexOfLast { it.isUser }
            return if (lastUserIndex < 0) {
                listOf(messages.last())
            } else {
                messages.subList(lastUserIndex, messages.size)
            }
        }
}

/**
 * Manages chat state and streams LLM responses.
 * Scoped to the ChatOverlayManager lifecycle (not a ViewModel subclass
 * since we're running in a Service, not an Activity).
 */
class ChatOverlayViewModel(
    private val speechManager: SpeechRecognizerManager,
    private val llmProvider: LlmProvider,
    private val ttsProvider: TtsProvider? = null,
    systemPrompt: String
) {

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main)
    private var streamJob: Job? = null
    private val conversationHistory = mutableListOf<Message>(
        Message.System(content = systemPrompt)
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            speechManager.state.collect { speechState ->
                _uiState.update { it.copy(speechState = speechState) }
                if (speechState is SpeechState.Result) {
                    if (speechState.finalText.isNotBlank()) {
                        onSend(speechState.finalText)
                    }
                    speechManager.resetToIdle()
                }
            }
        }
    }

    fun onStartListening() {
        streamJob?.cancel()
        ttsProvider?.stop()
        speechManager.startListening()
    }

    fun onStopListening() = speechManager.stopListening()

    fun onModeChange(mode: ChatMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun onSend(message: String) {
        if (message.isBlank()) return

        streamJob?.cancel()
        ttsProvider?.stop()

        conversationHistory.add(Message.User(content = message))
        appendUiMessage(ChatMessage(text = message, isUser = true))

        streamJob = scope.launch { streamFromLlm() }
    }

    fun stopPlayback() {
        streamJob?.cancel()
        ttsProvider?.stop()
    }

    fun destroy() {
        ttsProvider?.stop()
        scope.cancel()
    }

    // --- private helpers ---

    private suspend fun streamFromLlm() {
        appendUiMessage(ChatMessage(text = "", isUser = false, isStreaming = true))
        _uiState.update { it.copy(isStreaming = true) }

        val sendMs = System.currentTimeMillis()
        AgentDebug.log(TAG) { "timing: generate started" }

        try {
            var firstChunkLogged = false
            val response = llmProvider.generate(
                messages = conversationHistory.toList(),
                onChunk = { chunk ->
                    if (!firstChunkLogged) {
                        firstChunkLogged = true
                        AgentDebug.log(TAG) { "timing: first UI chunk at +${System.currentTimeMillis() - sendMs}ms" }
                    }
                    appendToLastMessage(chunk)
                }
            )

            AgentDebug.log(TAG) { "timing: stream complete at +${System.currentTimeMillis() - sendMs}ms" }

            val fullContent = response.content ?: ""
            conversationHistory.add(Message.Assistant(content = fullContent))
            finalizeStream()

            if (fullContent.isNotBlank()) {
                AgentDebug.log(TAG) { "timing: TTS speak start at +${System.currentTimeMillis() - sendMs}ms (${fullContent.length} chars)" }
                ttsProvider?.speak(fullContent)
                AgentDebug.log(TAG) { "timing: TTS speak done at +${System.currentTimeMillis() - sendMs}ms" }
            }
        } catch (e: CancellationException) {
            finalizeStream()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LLM generate failed", e)
            replaceLastMessage(
                ChatMessage(
                    text = "Sorry, I couldn't get a response. ${e.message ?: "Unknown error"}",
                    isUser = false
                )
            )
        }
    }

    private fun appendUiMessage(message: ChatMessage) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { it.copy(isStreaming = false) } + message
            )
        }
    }

    private fun appendToLastMessage(chunk: String) {
        _uiState.update { state ->
            val updated = state.messages.toMutableList()
            val last = updated.last()
            updated[updated.lastIndex] = last.copy(text = last.text + chunk)
            state.copy(messages = updated)
        }
    }

    private fun replaceLastMessage(message: ChatMessage) {
        _uiState.update { state ->
            val updated = state.messages.toMutableList()
            updated[updated.lastIndex] = message
            state.copy(messages = updated, isStreaming = false)
        }
    }

    private fun finalizeStream() {
        _uiState.update { state ->
            val updated = state.messages.toMutableList()
            updated[updated.lastIndex] = updated.last().copy(isStreaming = false)
            state.copy(messages = updated, isStreaming = false)
        }
    }

    companion object {
        private const val TAG = "ChatOverlayViewModel"
    }
}
