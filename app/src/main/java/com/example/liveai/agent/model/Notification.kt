package com.example.liveai.agent.model

import java.util.UUID

data class Notification(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val type: NotificationType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val delivered: Boolean = false
)

enum class NotificationType {
    TASK_COMPLETE,
    TASK_FAILED,
    TASK_PROGRESS,
    TASK_CANCELLED
}
