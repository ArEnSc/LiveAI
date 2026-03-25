package com.example.liveai.agent.model

data class ToolCallRequest(
    val id: String,
    val functionName: String,
    val argumentsJson: String
)
