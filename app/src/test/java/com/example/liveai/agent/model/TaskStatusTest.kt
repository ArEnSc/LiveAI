package com.example.liveai.agent.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStatusTest {

    @Test
    fun `QUEUED can transition to RUNNING`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.QUEUED, TaskStatus.RUNNING))
    }

    @Test
    fun `QUEUED can transition to SUSPENDED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.QUEUED, TaskStatus.SUSPENDED))
    }

    @Test
    fun `QUEUED can transition to CANCELLED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.QUEUED, TaskStatus.CANCELLED))
    }

    @Test
    fun `RUNNING can transition to COMPLETED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.RUNNING, TaskStatus.COMPLETED))
    }

    @Test
    fun `RUNNING can transition to FAILED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.RUNNING, TaskStatus.FAILED))
    }

    @Test
    fun `RUNNING can transition to SUSPENDED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.RUNNING, TaskStatus.SUSPENDED))
    }

    @Test
    fun `RUNNING can transition to CANCELLED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.RUNNING, TaskStatus.CANCELLED))
    }

    @Test
    fun `SUSPENDED can transition to RUNNING`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.SUSPENDED, TaskStatus.RUNNING))
    }

    @Test
    fun `SUSPENDED can transition to QUEUED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.SUSPENDED, TaskStatus.QUEUED))
    }

    @Test
    fun `SUSPENDED can transition to CANCELLED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.SUSPENDED, TaskStatus.CANCELLED))
    }

    @Test
    fun `COMPLETED cannot transition to RUNNING`() {
        assertFalse(TaskStatus.isValidTransition(TaskStatus.COMPLETED, TaskStatus.RUNNING))
    }

    @Test
    fun `COMPLETED cannot transition to any state`() {
        TaskStatus.entries.forEach { target ->
            assertFalse(
                "COMPLETED should not transition to $target",
                TaskStatus.isValidTransition(TaskStatus.COMPLETED, target)
            )
        }
    }

    @Test
    fun `FAILED cannot transition to any state`() {
        TaskStatus.entries.forEach { target ->
            assertFalse(
                "FAILED should not transition to $target",
                TaskStatus.isValidTransition(TaskStatus.FAILED, target)
            )
        }
    }

    @Test
    fun `CANCELLED cannot transition to any state`() {
        TaskStatus.entries.forEach { target ->
            assertFalse(
                "CANCELLED should not transition to $target",
                TaskStatus.isValidTransition(TaskStatus.CANCELLED, target)
            )
        }
    }

    @Test
    fun `QUEUED cannot transition to COMPLETED directly`() {
        assertFalse(TaskStatus.isValidTransition(TaskStatus.QUEUED, TaskStatus.COMPLETED))
    }

    @Test
    fun `QUEUED cannot transition to FAILED directly`() {
        assertFalse(TaskStatus.isValidTransition(TaskStatus.QUEUED, TaskStatus.FAILED))
    }
}
