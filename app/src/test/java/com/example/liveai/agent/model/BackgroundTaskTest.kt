package com.example.liveai.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundTaskTest {

    private fun createTask(
        status: TaskStatus = TaskStatus.QUEUED,
        result: TaskResult? = null
    ) = BackgroundTask(
        id = "t1",
        instructions = "Summarize PDF",
        createdAt = System.currentTimeMillis(),
        status = status,
        result = result
    )

    @Test
    fun `new task has QUEUED status`() {
        val task = createTask()
        assertEquals(TaskStatus.QUEUED, task.status)
    }

    @Test
    fun `new task has null result`() {
        val task = createTask()
        assertNull(task.result)
    }

    @Test
    fun `task with success result`() {
        val result = TaskResult.Success(
            summary = "Lease renewal for 2400",
            fullContent = "Full document summary...",
            durationMs = 5000
        )
        val task = createTask(status = TaskStatus.COMPLETED, result = result)
        assertEquals(TaskStatus.COMPLETED, task.status)
        val success = task.result as TaskResult.Success
        assertEquals("Lease renewal for 2400", success.summary)
        assertEquals(5000L, success.durationMs)
    }

    @Test
    fun `task with failure result`() {
        val result = TaskResult.Failure(
            error = "Network timeout",
            durationMs = 3000
        )
        val task = createTask(status = TaskStatus.FAILED, result = result)
        assertEquals(TaskStatus.FAILED, task.status)
        val failure = task.result as TaskResult.Failure
        assertEquals("Network timeout", failure.error)
    }

    @Test
    fun `default priority is NORMAL`() {
        val task = createTask()
        assertEquals(TaskPriority.NORMAL, task.priority)
    }

    @Test
    fun `default progress is empty`() {
        val task = createTask()
        assertEquals("", task.progress.phase)
        assertNull(task.progress.percent)
        assertNull(task.progress.detail)
    }

    @Test
    fun `task with progress`() {
        val task = createTask().copy(
            progress = TaskProgress(
                phase = "summarizing",
                percent = 0.6f,
                detail = "page 7 of 12"
            )
        )
        assertEquals("summarizing", task.progress.phase)
        assertEquals(0.6f, task.progress.percent)
        assertEquals("page 7 of 12", task.progress.detail)
    }

    @Test
    fun `updated instructions tracked separately`() {
        val task = createTask().copy(
            updatedInstructions = "Also extract rent amount"
        )
        assertEquals("Summarize PDF", task.instructions)
        assertEquals("Also extract rent amount", task.updatedInstructions)
    }
}
