package com.example.liveai.agent.model

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: String
)
