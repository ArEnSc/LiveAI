package com.example.liveai.agent.model

data class BackgroundTask(
    val id: String,
    val instructions: String,
    val updatedInstructions: String? = null,
    val spawnedFrom: String? = null,
    val createdAt: Long,
    val updatedAt: Long? = null,
    val status: TaskStatus,
    val progress: TaskProgress = TaskProgress(),
    val result: TaskResult? = null,
    val priority: TaskPriority = TaskPriority.NORMAL
)

enum class TaskStatus {
    QUEUED,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        private val validTransitions: Map<TaskStatus, Set<TaskStatus>> = mapOf(
            QUEUED to setOf(RUNNING, SUSPENDED, CANCELLED),
            RUNNING to setOf(SUSPENDED, COMPLETED, FAILED, CANCELLED),
            SUSPENDED to setOf(QUEUED, RUNNING, CANCELLED),
            COMPLETED to emptySet(),
            FAILED to emptySet(),
            CANCELLED to emptySet()
        )

        fun isValidTransition(from: TaskStatus, to: TaskStatus): Boolean {
            return validTransitions[from]?.contains(to) == true
        }
    }
}

data class TaskProgress(
    val phase: String = "",
    val percent: Float? = null,
    val detail: String? = null
)

sealed interface TaskResult {
    data class Success(
        val summary: String,
        val fullContent: String,
        val durationMs: Long
    ) : TaskResult

    data class Failure(
        val error: String,
        val durationMs: Long
    ) : TaskResult
}

enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH
}
