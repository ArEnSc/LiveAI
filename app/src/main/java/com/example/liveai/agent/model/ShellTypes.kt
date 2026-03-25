package com.example.liveai.agent.model

data class ShellCommand(
    val command: String,
    val args: List<String> = emptyList(),
    val workingDir: String? = null,
    val timeoutMs: Long = 30_000L,
    val maxOutputBytes: Int = 1_048_576
)

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val truncated: Boolean = false
)

data class BundledBinary(
    val name: String,
    val binaryName: String,
    val description: String,
    val allowedArgs: List<String>? = null,
    val maxTimeoutMs: Long = 300_000L
)
