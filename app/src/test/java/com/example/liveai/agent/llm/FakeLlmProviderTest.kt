package com.example.liveai.agent.llm

import com.example.liveai.agent.model.FinishReason
import com.example.liveai.agent.model.LlmResponse
import com.example.liveai.agent.model.Message
import com.example.liveai.agent.model.ToolCallRequest
import com.example.liveai.agent.model.ToolDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLlmProviderTest {

    @Test
    fun `returns responses in sequence`() = runTest {
        val provider = FakeLlmProvider(
            responses = listOf(
                LlmResponse(content = "first"),
                LlmResponse(content = "second")
            )
        )

        val messages = listOf(Message.User(content = "hi"))

        val r1 = provider.generate(messages)
        assertEquals("first", r1.content)

        val r2 = provider.generate(messages)
        assertEquals("second", r2.content)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when responses exhausted`() = runTest {
        val provider = FakeLlmProvider(responses = listOf(LlmResponse(content = "only")))
        val messages = listOf(Message.User(content = "hi"))

        provider.generate(messages)
        provider.generate(messages) // should throw
    }

    @Test
    fun `tracks call count`() = runTest {
        val provider = FakeLlmProvider(
            responses = listOf(LlmResponse(content = "a"), LlmResponse(content = "b"))
        )

        assertEquals(0, provider.generateCallCount)
        provider.generate(listOf(Message.User(content = "1")))
        assertEquals(1, provider.generateCallCount)
        provider.generate(listOf(Message.User(content = "2")))
        assertEquals(2, provider.generateCallCount)
    }

    @Test
    fun `records received messages`() = runTest {
        val provider = FakeLlmProvider(responses = listOf(LlmResponse(content = "ok")))
        val messages = listOf(
            Message.System(content = "You are helpful"),
            Message.User(content = "hello")
        )

        provider.generate(messages)

        assertEquals(1, provider.receivedMessages.size)
        assertEquals(2, provider.receivedMessages[0].size)
    }

    @Test
    fun `records received tools`() = runTest {
        val provider = FakeLlmProvider(responses = listOf(LlmResponse(content = "ok")))
        val tools = listOf(
            ToolDefinition("read_screen", "Reads the screen", "{}")
        )

        provider.generate(listOf(Message.User(content = "hi")), tools)

        assertEquals(1, provider.receivedTools.size)
        assertEquals("read_screen", provider.receivedTools[0][0].name)
    }

    @Test
    fun `returns tool calls when scripted`() = runTest {
        val toolCall = ToolCallRequest("call_1", "read_screen", "{}")
        val provider = FakeLlmProvider(
            responses = listOf(
                LlmResponse(
                    content = null,
                    toolCalls = listOf(toolCall),
                    finishReason = FinishReason.TOOL_CALLS
                )
            )
        )

        val response = provider.generate(listOf(Message.User(content = "what's on screen?")))

        assertEquals(null, response.content)
        assertEquals(1, response.toolCalls.size)
        assertEquals("read_screen", response.toolCalls[0].functionName)
        assertEquals(FinishReason.TOOL_CALLS, response.finishReason)
    }

    @Test
    fun `streams chunks via callback`() = runTest {
        val provider = FakeLlmProvider(responses = listOf(LlmResponse(content = "hi")))
        val chunks = mutableListOf<String>()

        provider.generate(
            messages = listOf(Message.User(content = "yo")),
            onChunk = { chunks.add(it) }
        )

        assertEquals(listOf("h", "i"), chunks)
    }

    @Test
    fun `no streaming callback when content is null`() = runTest {
        val provider = FakeLlmProvider(
            responses = listOf(LlmResponse(content = null, toolCalls = listOf(
                ToolCallRequest("c1", "tool", "{}")
            )))
        )
        val chunks = mutableListOf<String>()

        provider.generate(
            messages = listOf(Message.User(content = "yo")),
            onChunk = { chunks.add(it) }
        )

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `reset clears all tracking`() = runTest {
        val provider = FakeLlmProvider(
            responses = listOf(LlmResponse(content = "a"), LlmResponse(content = "b"))
        )
        provider.generate(listOf(Message.User(content = "1")))

        assertEquals(1, provider.generateCallCount)
        assertEquals(1, provider.receivedMessages.size)

        provider.reset()

        assertEquals(0, provider.generateCallCount)
        assertTrue(provider.receivedMessages.isEmpty())
    }
}
