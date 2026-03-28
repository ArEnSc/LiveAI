package com.example.liveai.agent.llm

import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.chat.FunctionCall
import com.example.liveai.agent.model.InputSource
import com.example.liveai.agent.model.Message
import com.example.liveai.agent.model.ToolCallRequest
import com.example.liveai.agent.model.ToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the message/tool translation logic between agent models and SDK types.
 * These are pure unit tests — no network calls.
 */
class OpenAiLlmProviderTest {

    // --- Message.toSdkMessage() ---

    @Test
    fun `System message translates to SDK system role`() {
        val msg = Message.System(content = "You are helpful")
        val sdk = msg.toSdkMessage()

        assertEquals(ChatRole.System, sdk.role)
        assertEquals("You are helpful", sdk.content)
    }

    @Test
    fun `User message translates to SDK user role`() {
        val msg = Message.User(content = "Hello", source = InputSource.VOICE)
        val sdk = msg.toSdkMessage()

        assertEquals(ChatRole.User, sdk.role)
        assertEquals("Hello", sdk.content)
    }

    @Test
    fun `Assistant message with content only`() {
        val msg = Message.Assistant(content = "Hi there")
        val sdk = msg.toSdkMessage()

        assertEquals(ChatRole.Assistant, sdk.role)
        assertEquals("Hi there", sdk.content)
        assertNull(sdk.toolCalls)
    }

    @Test
    fun `Assistant message with tool calls`() {
        val msg = Message.Assistant(
            content = null,
            toolCalls = listOf(
                ToolCallRequest("call_1", "read_screen", "{}"),
                ToolCallRequest("call_2", "tap", """{"text":"Mom"}""")
            )
        )
        val sdk = msg.toSdkMessage()

        assertEquals(ChatRole.Assistant, sdk.role)
        assertNull(sdk.content)
        assertNotNull(sdk.toolCalls)
        assertEquals(2, sdk.toolCalls!!.size)

        val first = sdk.toolCalls!![0] as ToolCall.Function
        assertEquals("call_1", first.id.id)
        assertEquals("read_screen", first.function.name)
        assertEquals("{}", first.function.arguments)
    }

    @Test
    fun `ToolResult message translates to SDK tool role`() {
        val msg = Message.ToolResult(
            toolCallId = "call_1",
            toolName = "read_screen",
            content = "WhatsApp - Mom (2 new)"
        )
        val sdk = msg.toSdkMessage()

        assertEquals(ChatRole.Tool, sdk.role)
        assertEquals("WhatsApp - Mom (2 new)", sdk.content)
        assertEquals(ToolId("call_1"), sdk.toolCallId)
    }

    // --- ToolDefinition.toSdkTool() ---

    @Test
    fun `ToolDefinition translates to SDK Tool`() {
        val def = ToolDefinition(
            name = "read_screen",
            description = "Reads the current screen",
            parametersSchema = """{"type":"object","properties":{}}"""
        )
        val sdk = def.toSdkTool()

        assertNotNull(sdk)
        assertEquals("function", sdk.type.value)
        assertEquals("read_screen", sdk.function.name)
        assertEquals("Reads the current screen", sdk.function.description)
    }

    // --- ToolCall.toToolCallRequest() ---

    @Test
    fun `SDK ToolCall Function translates to ToolCallRequest`() {
        val sdkCall = ToolCall.Function(
            id = ToolId("call_99"),
            function = FunctionCall(
                nameOrNull = "tap",
                argumentsOrNull = """{"text":"Send"}"""
            )
        )
        val request = sdkCall.toToolCallRequest()

        assertNotNull(request)
        assertEquals("call_99", request!!.id)
        assertEquals("tap", request.functionName)
        assertEquals("""{"text":"Send"}""", request.argumentsJson)
    }

    // --- Round-trip: agent Message -> SDK -> back ---

    @Test
    fun `full conversation translates without data loss`() {
        val messages = listOf(
            Message.System(content = "You are a voice assistant"),
            Message.User(content = "What's on my screen?", source = InputSource.VOICE),
            Message.Assistant(
                toolCalls = listOf(ToolCallRequest("c1", "read_screen", "{}"))
            ),
            Message.ToolResult(
                toolCallId = "c1",
                toolName = "read_screen",
                content = "Home screen with 3 notifications"
            ),
            Message.Assistant(content = "You have 3 notifications on your home screen.")
        )

        val sdkMessages = messages.map { it.toSdkMessage() }

        assertEquals(5, sdkMessages.size)
        assertEquals(ChatRole.System, sdkMessages[0].role)
        assertEquals(ChatRole.User, sdkMessages[1].role)
        assertEquals(ChatRole.Assistant, sdkMessages[2].role)
        assertEquals(ChatRole.Tool, sdkMessages[3].role)
        assertEquals(ChatRole.Assistant, sdkMessages[4].role)

        // Verify tool call chain integrity
        val assistantToolCalls = sdkMessages[2].toolCalls!!
        assertEquals("c1", (assistantToolCalls[0] as ToolCall.Function).id.id)
        assertEquals(ToolId("c1"), sdkMessages[3].toolCallId)
    }

    @Test
    fun `empty tool calls list results in null SDK toolCalls`() {
        val msg = Message.Assistant(content = "Just text", toolCalls = emptyList())
        val sdk = msg.toSdkMessage()

        assertNull(sdk.toolCalls)
    }

    @Test
    fun `multiple tools translate correctly`() {
        val tools = listOf(
            ToolDefinition("read_screen", "Reads screen", """{"type":"object","properties":{}}"""),
            ToolDefinition("tap", "Taps element", """{"type":"object","properties":{"text":{"type":"string"}}}""")
        )

        val sdkTools = tools.map { it.toSdkTool() }
        assertEquals(2, sdkTools.size)
        assertEquals("read_screen", sdkTools[0].function.name)
        assertEquals("tap", sdkTools[1].function.name)
    }
}
