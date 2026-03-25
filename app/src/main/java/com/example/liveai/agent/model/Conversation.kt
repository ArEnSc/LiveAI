package com.example.liveai.agent.model

import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val systemPrompt: String = "",
    val maxMessages: Int = 100
)
