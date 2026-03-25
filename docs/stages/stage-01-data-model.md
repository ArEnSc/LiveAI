## Stage 1: Data Model

**Goal**: All data types that the system uses. Pure Kotlin, no Android deps.
This is the vocabulary everything else speaks.

**What's testable**: Unit tests — serialization, status transitions, priority ordering.

**Types**:
- `Message` (sealed: System, User, Assistant, ToolResult)
- `LlmResponse`, `ToolCallRequest`, `ToolDefinition`, `FinishReason`, `TokenUsage`
- `BackgroundTask`, `TaskStatus`, `TaskProgress`, `TaskResult`, `TaskPriority`
- `TtsUtterance`, `UtterancePriority`, `UtteranceSource`
- `AgentLoopState` (sealed), `AgentEvent` (sealed)
- `OrchestratorState`, `MainChannelState`, `TtsState`
- `Notification`, `NotificationType`
- `ShellCommand`, `ShellResult`, `BundledBinary`
- `InputSource`, `Conversation`, `LlmConfig`, `TtsConfig`

**Tests**:
- TaskStatus transitions: QUEUED→RUNNING valid, COMPLETED→RUNNING invalid
- UtterancePriority ordering: IMMEDIATE > MAIN > NOTIFICATION > LOW
- Message serialization round-trip (all variants)
- BackgroundTask with null result vs Success vs Failure
- AgentLoopState sealed exhaustiveness (when expressions cover all cases)

**Files**:
```
agent/model/
├── Message.kt
├── LlmResponse.kt
├── ToolCallRequest.kt
├── ToolDefinition.kt
├── AgentLoopState.kt
├── AgentEvent.kt
├── BackgroundTask.kt
├── TtsUtterance.kt
├── Notification.kt
├── OrchestratorState.kt
├── ShellTypes.kt
├── Conversation.kt
└── Configs.kt                    — LlmConfig, TtsConfig
```

**Status**: Not Started
