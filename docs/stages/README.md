# LiveAI Agent System — Stage Index

Each stage is a self-contained file with goal, tests, and files.

## Quick Reference

| # | Stage | Type | Test | Status | File |
|---|-------|------|------|--------|------|
| 1 | Data Model | Pure Kotlin | JUnit | Not Started | [stage-01](stage-01-data-model.md) |
| 2 | Tool Call Bubbles UI | Compose | Preview | Not Started | [stage-02](stage-02-chat-ui-tool-call-bubbles.md) |
| 3 | Task List UI | Compose | Preview | Not Started | [stage-03](stage-03-task-list-ui.md) |
| 4 | Agent Status Bar UI | Compose | Preview | Not Started | [stage-04](stage-04-agent-status-bar-cancel-button.md) |
| 5 | TTS Indicator UI | Compose | Preview | Not Started | [stage-05](stage-05-tts-indicator-speaking-state.md) |
| 6 | Settings UI | Compose | Preview | Not Started | [stage-06](stage-06-settings-ui.md) |
| 7 | Provider Interfaces + Fakes | Pure Kotlin | JUnit | Not Started | [stage-07](stage-07-provider-interfaces-fakes.md) |
| 8a | ContextManager + Simple + Registry | Pure Kotlin | JUnit | Not Started | [stage-8a](stage-8a-context-manager.md) |
| 8b | LosslessContextManager (DAG + Room) | Room | Instrumented | Not Started | [stage-8b](stage-8b-lossless-dag.md) |
| 9 | AgentLoop + PauseGate + Prompt + Retry | Pure Kotlin | Turbine | Not Started | [stage-09](stage-09-agent-loop.md) |
| 10 | Orchestrator + TaskManager + TtsQueue | Pure Kotlin | Turbine | Not Started | [stage-10](stage-10-orchestrator-taskmanager-ttsqueue.md) |
| 11 | Wire UI to Orchestrator + Persistence | Integration | Turbine | Not Started | [stage-11](stage-11-wire-ui-to-orchestrator-persistence.md) |
| 12 | OpenAI LlmProvider + Retry | Network | MockWebServer | Not Started | [stage-12](stage-12-openai-llmprovider-retrystrategy.md) |
| 13 | TTS Providers | Audio | MockWebServer | Not Started | [stage-13](stage-13-tts-providers.md) |
| 14 | Accessibility Tools | Platform | Instrumented | Not Started | [stage-14](stage-14-accessibility-tools.md) |
| 15 | Shell + Binary Tools | Platform | JUnit | Not Started | [stage-15](stage-15-shell-binary-execution-tools.md) |
| 16 | Camera Tools | Platform | Instrumented | Not Started | [stage-16](stage-16-camera-tools.md) |
| 17 | AppContainer + Settings Wiring | DI | JUnit | Not Started | [stage-17](stage-17-appcontainer-settings-wiring.md) |
| 18 | NetworkMonitor | Platform | JUnit + Instrumented | Not Started | [stage-18](stage-18-networkmonitor.md) |
| 19 | HapticFeedbackManager | Platform | JUnit | Not Started | [stage-19](stage-19-hapticfeedbackmanager.md) |
| 20 | Notification System (Blind-Optimized) | Platform | Instrumented | Not Started | [stage-20](stage-20-notification-system-blind-optimized.md) |
| 21 | Permission Flow Update | Platform | Instrumented | Not Started | [stage-21](stage-21-permission-flow-update.md) |

## Work Waves (Parallelization)

```
WAVE 1:
└── Stage 1:  Data Model                         ← must go first, but small

WAVE 2 (all 5 in parallel after Stage 1):
├── Stage 2:  Tool Call Bubbles UI                ← fake data
├── Stage 3:  Task List UI                        ← fake data
├── Stage 4:  Agent Status Bar UI                 ← fake data
├── Stage 5:  TTS Indicator UI                    ← fake data
└── Stage 6:  Settings UI                         ← fake data

WAVE 3 (all in parallel, no deps on each other):
├── Stage 7:  Provider Interfaces + Fakes         ← pure Kotlin
└── Stage 8a: ContextManager + Simple + Registry  ← pure Kotlin

WAVE 4 (2 in parallel):
├── Stage 8b: Lossless DAG + Room                 ← needs 8a interface only
└── Stage 9:  AgentLoop + PauseGate               ← needs 7 + 8a interface only

WAVE 5:
└── Stage 10: Orchestrator + TaskManager          ← needs 9

WAVE 6:
└── Stage 11: Wire UI ← Orchestrator             ← needs waves 2 + 5

WAVE 7 (all 9 in parallel):
├── Stage 12: OpenAI LlmProvider + Retry          ← MockWebServer
├── Stage 13: TTS Providers                       ← MockWebServer
├── Stage 14: Accessibility Tools                 ← mock node trees
├── Stage 15: Shell + Binary Tools                ← pure JUnit
├── Stage 16: Camera Tools                        ← instrumented
├── Stage 18: NetworkMonitor                      ← instrumented
├── Stage 19: HapticFeedbackManager               ← JUnit + vibrator mock
├── Stage 20: Notification System                 ← instrumented
└── Stage 21: Permission Flow                     ← instrumented

WAVE 8:
└── Stage 17: AppContainer + Settings Wiring      ← final integration
```

## Dependency Graph

```
Stage 1:  Data Model                          ← pure Kotlin, no deps
    │
    ├──► Stage 2:  Tool Call Bubbles UI       ← needs Message types
    ├──► Stage 3:  Task List UI               ← needs BackgroundTask types
    ├──► Stage 4:  Agent Status Bar UI        ← needs AgentLoopState
    ├──► Stage 5:  TTS Indicator UI           ← needs TtsState
    └──► Stage 6:  Settings UI                ← needs Config types
              │
    (all UI stages can run in parallel after Stage 1)
              │
    ┌─────────┘
    │
    ├──► Stage 7:  Provider Interfaces        ← pure Kotlin
    │
    ├──► Stage 8a: ContextManager + Simple   ← pure Kotlin
    │         │     + ToolRegistry
    │         │
    │         ├──► Stage 8b: Lossless DAG    ← Room + compaction (can parallel with 9)
    │         │
    │         ▼
    ├──► Stage 9:  AgentLoop + PauseGate      ← needs 7 + 8a
    │         │
    │         ▼
    ├──► Stage 10: Orchestrator + Tasks       ← needs 9
    │         │
    │         ▼
    └──► Stage 11: Wire UI ← Orchestrator     ← needs 2-6 + 10
              │
              ├──► Stage 12: OpenAI Provider  ← can parallel
              ├──► Stage 13: TTS Providers    ← can parallel
              ├──► Stage 14: Accessibility    ← can parallel
              ├──► Stage 15: Shell Tools      ← can parallel
              ├──► Stage 16: Camera Tools     ← can parallel
              ├──► Stage 18: NetworkMonitor   ← can parallel
              ├──► Stage 19: HapticFeedback   ← can parallel
              ├──► Stage 20: Notifications    ← can parallel
              └──► Stage 21: Permission Flow  ← can parallel
                        │
                        ▼
                   Stage 17: AppContainer     ← final integration
```

## What Fakes Unblock

| Fake | Stages It Unblocks |
|------|--------------------|
| Fake data in ViewModel | 2, 3, 4, 5, 6 — all UI visible before any backend exists |
| `FakeLlmProvider` | 9, 10, 11 — agent loop + orchestrator work without OpenAI |
| `FakeTtsProvider` | 10, 11 — orchestrator works without audio hardware |
| `FakeContextManager` | 9, 10 — agent loop works without Room/DAG |
| `SimpleContextManager` | Full app runs without lossless DAG — swap in 8b when ready |

**Key insight**: Stage 8b (DAG + Room) is the longest single piece of work but
never blocks anything. The entire app runs on `SimpleContextManager` until 8b
is done, then swap implementations in `AppContainer` — one line change.
