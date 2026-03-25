package com.example.liveai.agent.model

data class OrchestratorState(
    val mainChannel: MainChannelState = MainChannelState(),
    val tasks: List<BackgroundTask> = emptyList(),
    val ttsState: TtsState = TtsState.Silent,
    val accessibilityEnabled: Boolean = false
)

data class MainChannelState(
    val messages: List<Message> = emptyList(),
    val agentState: AgentLoopState = AgentLoopState.Idle,
    val isListening: Boolean = false,
    val partialSpeech: String? = null
)

sealed interface TtsState {
    data object Silent : TtsState
    data class Speaking(
        val utterance: TtsUtterance,
        val queueSize: Int
    ) : TtsState
}
