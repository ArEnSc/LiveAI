package com.example.liveai.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopStateTest {

    @Test
    fun `exhaustive when covers all states`() {
        val states: List<AgentLoopState> = listOf(
            AgentLoopState.Idle,
            AgentLoopState.Queued,
            AgentLoopState.Generating(iteration = 1),
            AgentLoopState.ExecutingTools(
                iteration = 1,
                toolCalls = emptyList(),
                completedCount = 0
            ),
            AgentLoopState.Cancelled,
            AgentLoopState.Error(message = "fail", recoverable = false)
        )

        states.forEach { state ->
            val label = when (state) {
                is AgentLoopState.Idle -> "idle"
                is AgentLoopState.Queued -> "queued"
                is AgentLoopState.Generating -> "generating"
                is AgentLoopState.ExecutingTools -> "executing"
                is AgentLoopState.Cancelled -> "cancelled"
                is AgentLoopState.Error -> "error"
            }
            assertTrue(label.isNotEmpty())
        }

        assertEquals(6, states.size)
    }

    @Test
    fun `Generating tracks iteration`() {
        val state = AgentLoopState.Generating(iteration = 3)
        assertEquals(3, state.iteration)
    }

    @Test
    fun `ExecutingTools tracks progress`() {
        val calls = listOf(
            ToolCallRequest("c1", "read_screen", "{}"),
            ToolCallRequest("c2", "tap", """{"text":"Mom"}""")
        )
        val state = AgentLoopState.ExecutingTools(
            iteration = 1,
            toolCalls = calls,
            completedCount = 1
        )
        assertEquals(2, state.toolCalls.size)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun `Error has message and recoverable flag`() {
        val state = AgentLoopState.Error(message = "Rate limited", recoverable = true)
        assertEquals("Rate limited", state.message)
        assertEquals(true, state.recoverable)
    }
}
