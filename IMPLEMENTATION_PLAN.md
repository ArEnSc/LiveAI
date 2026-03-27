# LiveAI Agent System — Implementation Plan

> **Full stage details**: [docs/stages/](docs/stages/README.md)
> Each stage is its own file with goal, tests, and files.

## Approach: UI-First, Each Stage Testable

Build from what the user sees/hears outward. Every stage produces either a
visible UI change or a unit-testable component. Fake data drives the UI
until real backends are wired in.

### What Already Exists

```
✅ Chat overlay (tab + panel)         — ChatOverlayManager
✅ Message bubbles (user + assistant)  — MessageBubble
✅ Chat input bar + send               — ChatInputBar
✅ Push-to-talk + speech recognition   — PushToTalkButton, SpeechRecognizerManager
✅ Mock streaming responses            — ChatOverlayViewModel.generateMockResponse()
✅ TTS engine (ONNX, local)            — tts-demo module (PocketTtsEngine)
✅ OpenAI SDK dependency               — in build.gradle, not yet used
```

## Stage Quick Reference

| # | Stage | Type | Test | Status |
|---|-------|------|------|--------|
| [1](docs/stages/stage-01-data-model.md) | Data Model | Pure Kotlin | JUnit | Not Started |
| [2](docs/stages/stage-02-chat-ui-tool-call-bubbles.md) | Tool Call Bubbles UI | Compose | Preview | Not Started |
| [3](docs/stages/stage-03-task-list-ui.md) | Task List UI | Compose | Preview | Not Started |
| [4](docs/stages/stage-04-agent-status-bar-cancel-button.md) | Agent Status Bar UI | Compose | Preview | Not Started |
| [5](docs/stages/stage-05-tts-indicator-speaking-state.md) | TTS Indicator UI | Compose | Preview | Not Started |
| [6](docs/stages/stage-06-settings-ui.md) | Settings UI | Compose | Preview | Not Started |
| [7](docs/stages/stage-07-provider-interfaces-fakes.md) | Provider Interfaces + Fakes | Pure Kotlin | JUnit | Not Started |
| [8a](docs/stages/stage-8a-context-manager.md) | ContextManager + Simple + Registry + TokenCounter | Pure Kotlin | JUnit | Not Started |
| [8b](docs/stages/stage-8b-lossless-dag.md) | LosslessContextManager (DAG + Room) | Room | Instrumented | Not Started |
| [9](docs/stages/stage-09-agent-loop.md) | AgentLoop + PauseGate + Prompt + Retry | Pure Kotlin | Turbine | Not Started |
| [10](docs/stages/stage-10-orchestrator-taskmanager-ttsqueue.md) | Orchestrator + TaskManager + TtsQueue | Pure Kotlin | Turbine | Not Started |
| [11](docs/stages/stage-11-wire-ui-to-orchestrator-persistence.md) | Wire UI to Orchestrator + Persistence | Integration | Turbine | Not Started |
| [12](docs/stages/stage-12-openai-llmprovider-retrystrategy.md) | OpenAI LlmProvider + RetryStrategy | Network | MockWebServer | Not Started |
| [13](docs/stages/stage-13-tts-providers.md) | TTS Providers | Audio | MockWebServer | Not Started |
| [14](docs/stages/stage-14-accessibility-tools.md) | Accessibility Tools | Platform | Instrumented | Not Started |
| [15](docs/stages/stage-15-shell-binary-execution-tools.md) | Shell + Binary Tools | Platform | JUnit | Not Started |
| [16](docs/stages/stage-16-camera-tools.md) | Camera Tools | Platform | Instrumented | Not Started |
| [17](docs/stages/stage-17-appcontainer-settings-wiring.md) | AppContainer + Settings Wiring | DI | JUnit | Not Started |
| [18](docs/stages/stage-18-networkmonitor.md) | NetworkMonitor | Platform | JUnit + Instrumented | Not Started |
| [19](docs/stages/stage-19-hapticfeedbackmanager.md) | HapticFeedbackManager | Platform | JUnit | Not Started |
| [20](docs/stages/stage-20-notification-system-blind-optimized.md) | Notification System (Blind-Optimized) | Platform | Instrumented | Not Started |
| [21](docs/stages/stage-21-permission-flow-update.md) | Permission Flow Update | Platform | Instrumented | Not Started |

## Work Waves

```
WAVE 1:  Stage 1 (Data Model)
WAVE 2:  Stages 2-6 in parallel (all UI, fake data)
WAVE 3:  Stages 7 + 8a in parallel (interfaces)
WAVE 4:  Stages 8b + 9 in parallel (DAG + AgentLoop)
WAVE 5:  Stage 10 (Orchestrator)
WAVE 6:  Stage 11 (Wire UI)
WAVE 7:  Stages 12-16, 18-21 all in parallel (providers + tools + platform)
WAVE 8:  Stage 17 (AppContainer — final integration)
```

## Project Folder Structure

```
app/src/main/java/com/example/liveai/
│
├── agent/                                  ← CORE AGENT SYSTEM
│   ├── model/                              ← Stage 1: Data types (13 files)
│   ├── llm/                                ← Stage 7 + 12: LLM providers
│   ├── tts/                                ← Stage 7 + 13: TTS providers
│   ├── context/                            ← Stage 8a + 8b: Context management
│   │   ├── simple/                         ← In-memory (background tasks)
│   │   ├── fake/                           ← Tests
│   │   └── lossless/                       ← DAG + Room (main channel)
│   │       ├── db/entity/ + dao/           ← Room layer
│   │       ├── compaction/                 ← DAG construction
│   │       ├── assembly/                   ← Context window building
│   │       ├── retrieval/                  ← Search + expand
│   │       ├── redaction/                  ← Delete with DAG healing
│   │       └── integrity/                  ← DAG consistency checks
│   ├── token/                              ← Stage 8a: Token counting
│   ├── tool/                               ← Stage 8a + 14-16: All tools
│   ├── prompt/                             ← Stage 9: System prompt management
│   ├── shell/                              ← Stage 15: Shell execution
│   ├── camera/                             ← Stage 16: Camera capture
│   ├── orchestrator/                       ← Stage 10: Orchestration
│   ├── task/                               ← Stage 10: Task management
│   ├── network/                            ← Stage 18: Network monitoring
│   ├── haptic/                             ← Stage 19: Haptic feedback
│   ├── notification/                       ← Stage 20: Notification system
│   ├── AgentLoop.kt                        ← Stage 9
│   ├── AgentLoopConfig.kt
│   └── PauseGate.kt
│
├── accessibility/                          ← Stage 14
├── chat/                                   ← Stages 2-6, 11 (Overlay UI)
├── settings/                               ← Stage 6 + 17
├── AppContainer.kt                         ← Stage 17
├── LiveAIApplication.kt                    ← Stage 17
├── AppStateStore.kt                        ← Stage 11 (DataStore)
├── PermissionsActivity.kt                  ← Stage 21
├── MainActivity.kt
└── OverlayService.kt
```

## Test Dependencies

```toml
# Add to libs.versions.toml
turbine = "1.2.0"
coroutines-test = "1.9.0"
mockwebserver = "4.12.0"
room = "2.6.1"
ktoken = "0.4.0"
datastore = "1.1.1"
camerax = "1.4.0"
```

## Message Format (OpenAI-Compatible)

```
Message.System   → system prompt (built by SystemPromptBuilder)
Message.User     → user input (voice or keyboard, optional imageUri)
Message.Assistant → LLM response (content and/or toolCalls)
Message.ToolResult → tool execution result (toolCallId back-reference)
```
