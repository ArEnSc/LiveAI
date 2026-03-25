package com.example.liveai.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    @Test
    fun `System message has content`() {
        val msg = Message.System(content = "You are helpful")
        assertEquals("You are helpful", msg.content)
        assertNotNull(msg.id)
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun `User message has source and optional image`() {
        val msg = Message.User(content = "hello", source = InputSource.VOICE)
        assertEquals("hello", msg.content)
        assertEquals(InputSource.VOICE, msg.source)
        assertNull(msg.imageUri)
    }

    @Test
    fun `User message with image URI`() {
        val msg = Message.User(
            content = "What's this?",
            imageUri = "file:///cache/photo.jpg"
        )
        assertEquals("file:///cache/photo.jpg", msg.imageUri)
    }

    @Test
    fun `Assistant message with content only`() {
        val msg = Message.Assistant(content = "Hi there")
        assertEquals("Hi there", msg.content)
        assertTrue(msg.toolCalls.isEmpty())
        assertEquals(false, msg.isStreaming)
    }

    @Test
    fun `Assistant message with tool calls and no content`() {
        val toolCall = ToolCallRequest(
            id = "call_1",
            functionName = "read_screen",
            argumentsJson = "{}"
        )
        val msg = Message.Assistant(toolCalls = listOf(toolCall))
        assertNull(msg.content)
        assertEquals(1, msg.toolCalls.size)
        assertEquals("read_screen", msg.toolCalls[0].functionName)
    }

    @Test
    fun `ToolResult references a tool call`() {
        val msg = Message.ToolResult(
            toolCallId = "call_1",
            toolName = "read_screen",
            content = "WhatsApp - Mom (2 new)"
        )
        assertEquals("call_1", msg.toolCallId)
        assertEquals("read_screen", msg.toolName)
        assertEquals(false, msg.isError)
    }

    @Test
    fun `ToolResult with error`() {
        val msg = Message.ToolResult(
            toolCallId = "call_2",
            toolName = "tap",
            content = "Element not found",
            isError = true
        )
        assertEquals(true, msg.isError)
    }

    @Test
    fun `each message gets unique ID`() {
        val msg1 = Message.User(content = "a")
        val msg2 = Message.User(content = "b")
        assertNotEquals(msg1.id, msg2.id)
    }

    @Test
    fun `default source is KEYBOARD`() {
        val msg = Message.User(content = "typed")
        assertEquals(InputSource.KEYBOARD, msg.source)
    }

    @Test
    fun `all message types implement Message interface`() {
        val messages: List<Message> = listOf(
            Message.System(content = "sys"),
            Message.User(content = "user"),
            Message.Assistant(content = "assistant"),
            Message.ToolResult(toolCallId = "c1", toolName = "t", content = "r")
        )
        assertEquals(4, messages.size)
        messages.forEach { msg ->
            assertNotNull(msg.id)
            assertTrue(msg.timestamp > 0)
        }
    }
}
