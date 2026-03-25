package com.example.liveai.agent.model

import java.util.UUID

data class TtsUtterance(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val priority: UtterancePriority,
    val source: UtteranceSource,
    val taskId: String? = null
)

enum class UtterancePriority {
    IMMEDIATE,
    MAIN,
    NOTIFICATION,
    LOW;

    // Ordinal ordering: IMMEDIATE(0) < MAIN(1) < NOTIFICATION(2) < LOW(3)
    // Lower ordinal = higher priority. Use ordinal for comparisons.
}

enum class UtteranceSource {
    MAIN_CHAT,
    TASK_COMPLETE,
    SYSTEM
}
