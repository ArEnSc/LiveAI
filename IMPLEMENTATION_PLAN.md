# LiveAI Agent System — Implementation Plan

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

---

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

---

## Stage 2: Chat UI — Tool Call Bubbles

**Goal**: New message bubble types that show when the agent is using tools.
Renders in the existing ChatPanel. Driven by fake data.

**What's visible**: Tool call messages appear in chat — "Using read_screen...",
"Result: WhatsApp open with 2 messages", with a distinct visual style.

**What's testable**: Composable renders correctly with different message types.

**New UI elements**:
```
┌──────────────────────────────┐
│ 🔧 Reading screen...         │  ← ToolCallBubble (in-progress)
│    read_screen               │
└──────────────────────────────┘

┌──────────────────────────────┐
│ ✓ Screen read (0.8s)         │  ← ToolCallBubble (complete)
│    WhatsApp - Chat list:     │
│    Mom (2 new), Work Group   │
└──────────────────────────────┘

┌──────────────────────────────┐
│ ✗ Error: Service not enabled │  ← ToolCallBubble (error)
└──────────────────────────────┘
```

**Changes**:
- Extend `ChatMessage` to support tool call display (or new sealed type)
- `ToolCallBubble` composable — shows tool name, status, duration, result preview
- `ChatPanel` renders tool call messages between user/assistant bubbles
- Fake data in ViewModel to demonstrate all states

**Tests**:
- ToolCallBubble renders with in-progress state (shows spinner + tool name)
- ToolCallBubble renders with complete state (shows checkmark + duration + result)
- ToolCallBubble renders with error state (shows X + error message)
- ChatPanel with mixed messages (user, tool call, assistant) renders in order
- Result text truncated beyond 2 lines with "Show more" (accessibility: full text readable)

**Files**:
```
chat/
├── ToolCallBubble.kt              — NEW
├── MessageBubble.kt               — MODIFIED: delegate to ToolCallBubble for tool messages
├── ChatPanel.kt                   — MODIFIED: handle new message types
└── ChatOverlayViewModel.kt        — MODIFIED: add fake tool call messages for demo
```

**Status**: Not Started

---

## Stage 3: Task List UI

**Goal**: Show background tasks in the overlay panel. Fake data.
Tab bar at top of panel switches between Chat and Tasks views.

**What's visible**: Task cards with progress bars, status badges, phase text.

```
┌──────────────────────────────────┐
│  [💬 Chat]  [📋 Tasks (2)]       │  ← tab bar
├──────────────────────────────────┤
│                                  │
│  ⏳ Summarize lease PDF    60%   │
│     ████████████░░░░░░░░         │
│     summarizing... page 7 of 12  │
│                    [Pause][Cancel]│
│                                  │
│  🕐 Check pharmacy hours         │
│     queued                       │
│                    [Cancel]      │
│                                  │
│  ✅ Read Mom's texts              │
│     completed in 2.3s            │
│     "dinner at 6, need a ride?"  │
│                    [Clear]       │
│                                  │
└──────────────────────────────────┘
```

**New UI elements**:
- `OverlayTabBar` — switches between Chat and Tasks
- `TaskListPanel` — LazyColumn of task cards
- `TaskCard` — individual task with progress bar, status, actions
- `TaskProgressBar` — animated progress with indeterminate mode
- `TaskStatusBadge` — QUEUED/RUNNING/SUSPENDED/COMPLETED/FAILED/CANCELLED

**Tests**:
- TaskCard renders each status correctly (QUEUED shows clock, RUNNING shows spinner, etc.)
- TaskProgressBar with percent=0.6 fills 60%
- TaskProgressBar with percent=null shows indeterminate animation
- TaskCard RUNNING shows Pause + Cancel buttons
- TaskCard SUSPENDED shows Resume + Cancel buttons
- TaskCard COMPLETED shows Clear button + result preview
- TaskCard QUEUED shows Cancel button only
- TaskListPanel with 0 tasks shows "No background tasks"
- Tab bar shows task count badge
- Tab bar switches between Chat and Tasks views

**Files**:
```
chat/
├── OverlayTabBar.kt               — NEW: Chat / Tasks tab switcher
├── TaskListPanel.kt               — NEW: scrollable task list
├── TaskCard.kt                    — NEW: individual task card
├── TaskProgressBar.kt             — NEW: animated progress
├── TaskStatusBadge.kt             — NEW: status indicator
├── ChatPanel.kt                   — MODIFIED: wrapped in tab container
└── ChatOverlayViewModel.kt        — MODIFIED: add fake tasks, tab state
```

**Status**: Not Started

---

## Stage 4: Agent Status Bar + Cancel Button

**Goal**: Visual indicator in chat showing what the agent is doing right now.
Cancel button to stop the current operation. Driven by `AgentLoopState`.

**What's visible**: Status bar between messages and input, showing current state.

```
Chat view during agent activity:

┌──────────────────────────────────┐
│  You: What's on my screen?       │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 🔄 Generating (iter 2)... │  │  ← AgentStatusBar
│  │            [Cancel]        │  │
│  └────────────────────────────┘  │
│                                  │
│  [input bar]                     │
└──────────────────────────────────┘

States shown:
  Idle        → hidden
  Queued      → "Waiting..."
  Generating  → "Thinking..." with iteration count
  ExecutingTools → "Using read_screen..." with tool name
  Cancelled   → "Cancelled" (brief flash, then hidden)
  Error       → "Error: ..." with retry button
```

**Tests**:
- AgentStatusBar hidden when Idle
- AgentStatusBar shows "Thinking..." for Generating
- AgentStatusBar shows tool name for ExecutingTools
- Cancel button visible when Generating or ExecutingTools
- Cancel button fires onCancel callback
- Error state shows message + retry button

**Files**:
```
chat/
├── AgentStatusBar.kt              — NEW
├── ChatPanel.kt                   — MODIFIED: insert status bar above input
└── ChatOverlayViewModel.kt        — MODIFIED: expose agentState, fake transitions
```

**Status**: Not Started

---

## Stage 5: TTS Indicator + Speaking State

**Goal**: Visual feedback when the app is speaking. Speaker icon animation
in the chat tab. Queue indicator when multiple utterances pending.

**What's visible**: Chat tab pulses/animates while speaking. Status text shows
"Speaking..." or "2 queued".

```
Chat tab while speaking:

  ┌────┐
  │ 🔊 │  ← animated speaker icon (replaces chat icon)
  └────┘

Status bar while speaking:
  ┌────────────────────────────────┐
  │ 🔊 Speaking...  (1 queued)     │
  └────────────────────────────────┘
```

**Tests**:
- ChatTab shows speaker animation when TtsState.Speaking
- ChatTab shows normal icon when TtsState.Silent
- TTS status shows queue count when > 0
- Speaking state visible even when panel is collapsed (tab animates)

**Files**:
```
chat/
├── ChatTab.kt                     — MODIFIED: animate when speaking
├── TtsSpeakingIndicator.kt        — NEW: inline status component
└── ChatOverlayViewModel.kt        — MODIFIED: expose ttsState, fake speaking
```

**Status**: Not Started

---

## Stage 6: Settings UI

**Goal**: Settings screen accessible from the overlay. Provider selection
for LLM and TTS. API key entry. Accessibility service toggle.

**What's visible**: Settings panel or bottom sheet with dropdowns and input fields.

```
┌──────────────────────────────────┐
│  ⚙️ Settings                     │
├──────────────────────────────────┤
│                                  │
│  LLM Provider                    │
│  [▼ OpenAI (gpt-4o)           ] │
│                                  │
│  API Key                         │
│  [••••••••••••••••sk-xxx      ] │
│                                  │
│  TTS Provider                    │
│  [▼ Android Built-in           ] │
│                                  │
│  ─────────────────────────────   │
│                                  │
│  Accessibility Service           │
│  [  Not enabled  ] [Enable →]   │
│                                  │
│  Shell Access                    │
│  [✓ Enabled]                    │
│                                  │
└──────────────────────────────────┘
```

**Tests**:
- Provider dropdown shows all options (OpenAI, Anthropic for LLM; Pocket, OpenAI, Android for TTS)
- API key field masks input
- Accessibility toggle reflects actual system state
- Settings persist across panel close/reopen (SharedPreferences)
- Invalid API key format shows inline error

**Files**:
```
chat/
├── OverlayTabBar.kt               — MODIFIED: add Settings tab/gear icon
settings/
├── SettingsPanel.kt               — NEW: settings UI composable
├── ProviderSelector.kt            — NEW: dropdown for provider selection
├── ApiKeyField.kt                 — NEW: masked input with validation
└── SettingsState.kt               — NEW: data class for settings UI state
```

**Status**: Not Started

---

## Stage 7: Provider Interfaces + Fakes

**Goal**: Define `LlmProvider` and `TtsProvider` interfaces with fake implementations.
These are the testable seams that decouple UI from backends.

**What's testable**: Unit tests — fakes behave predictably, contracts are correct.

**Tests**:
- FakeLlmProvider returns canned responses in sequence
- FakeLlmProvider throws when exhausted
- FakeTtsProvider.speak() records text, spokenTexts list matches
- FakeTtsProvider.stop() cancels in-progress speak
- LlmProvider interface contract: generate() returns LlmResponse with correct shape
- TtsProvider interface contract: speak() suspends, stop() is immediate

**Files**:
```
agent/
├── llm/
│   ├── LlmProvider.kt
│   └── FakeLlmProvider.kt
├── tts/
│   ├── TtsProvider.kt
│   └── FakeTtsProvider.kt
```

**Status**: Not Started

---

## Stage 8: ConversationMemory + ToolRegistry

**Goal**: The two data structures the agent loop needs.

**What's testable**: Pure unit tests, no Android deps.

**Tests**:
- Memory: append 10, get 10 back
- Memory: truncation preserves system message, drops oldest
- Memory: inject() inserts system message at current position
- Memory: clear() empties but keeps system prompt
- Registry: register + execute returns result
- Registry: unknown tool returns error string (no crash)
- Registry: getDefinitions() returns schemas for all tools
- EchoTool: returns its input as output

**Files**:
```
agent/
├── memory/
│   └── ConversationMemory.kt
├── tool/
│   ├── Tool.kt
│   ├── ToolRegistry.kt
│   └── EchoTool.kt
```

**Status**: Not Started

---

## Stage 9: AgentLoop + PauseGate

**Goal**: The core agentic loop. Generate → tool calls → execute → loop.
PauseGate for cooperative suspend/resume at checkpoints.

**What's testable**: Turbine StateFlow tests, all with FakeLlmProvider.

**Tests**:
- Happy path: text response → states [Idle, Queued, Generating, Idle]
- Tool loop: toolCall then text → ExecutingTools, tool executed, provider called twice
- Multi-tool: 2 calls → both executed, both results in memory
- Max iterations: infinite tools → stops at limit
- Cancel during generate → Cancelled
- Cancel during tool exec → Cancelled
- Pause at gate → suspends, resume → continues from exact point
- Memory contains correct message sequence after completion
- PauseGate unit: pause() then check() blocks; resume() unblocks

**Files**:
```
agent/
├── AgentLoop.kt
├── AgentLoopConfig.kt
└── PauseGate.kt
```

**Status**: Not Started

---

## Stage 10: Orchestrator + TaskManager + TtsQueue

**Goal**: The orchestration layer. MainChannel for conversation, TaskManager
with SCRUD + suspend/resume, TtsQueue with priority, Router for notifications.

**What's testable**: Turbine tests for state, SCRUD operations, priority ordering.

### TaskManager SCRUD + Suspend/Resume

```
Search    — search(query) fuzzy matches on instructions/status
Create    — create(instructions, priority, tools) → BackgroundTask
Read      — get(id), getByStatus(status), getActive(), getHistory()
Update    — updateInstructions(id, new), reprioritize(id, priority)
Delete    — cancel(id), remove(id), clearCompleted(), clearAll()
Suspend   — pause(id), resume(id), pauseAll(), resumeAll()
```

**Tests**:
- Main chat: send → response + TTS enqueued
- spawn_task → task created, runs in background with own AgentLoop
- Task completes → notification injected into main chat
- Task completes while main speaking → notification queued (NOTIFICATION priority)
- SCRUD: search, get, update, cancel, remove, clearCompleted all work
- pause QUEUED → SUSPENDED, not dispatched
- pause RUNNING → SUSPENDED at next gate
- resume → continues from exact point
- TtsQueue: IMMEDIATE preempts, MAIN before NOTIFICATION
- TtsQueue: one utterance at a time
- Concurrency: 3 tasks, maxConcurrent=2 → 2 run, 1 queued
- Cancel main doesn't cancel background tasks

**Files**:
```
agent/
├── orchestrator/
│   ├── Orchestrator.kt
│   ├── MainChannel.kt
│   ├── Router.kt
│   └── TtsQueue.kt
├── task/
│   ├── TaskManager.kt
│   ├── BackgroundTaskRunner.kt
│   └── TaskTools.kt               — SpawnTaskTool, ListTasksTool, etc.
```

**Status**: Not Started

---

## Stage 11: Wire UI to Orchestrator

**Goal**: Replace fake data in the UI with real OrchestratorState.
Connect ChatOverlayViewModel to Orchestrator. Mock responses → real agent loop.

**What's visible**: Everything from Stages 2–6 now driven by real data.
Send a message → agent processes → response appears + TTS speaks.

**What's testable**: ViewModel with FakeLlmProvider + FakeTtsProvider.

**Tests**:
- Send message → response in chat state + TTS called
- Cancel → AgentLoop + TTS stopped
- Tool call → ToolCallBubble appears in chat
- Task spawned → appears in task list
- Task completes → notification in chat + TTS speaks
- Speech-in → AgentLoop → TTS out (full pipeline)
- AgentStatusBar reflects real agent state
- Task card buttons (pause/cancel/resume) trigger real operations

**Files**:
```
chat/
├── ChatOverlayViewModel.kt        — REWRITTEN: observe OrchestratorState
├── ChatOverlayManager.kt          — MODIFIED: inject Orchestrator

AppContainer.kt                     — NEW: composition root
LiveAIApplication.kt                — NEW: Application subclass
```

**Status**: Not Started

---

## Stage 12: OpenAI LlmProvider

**Goal**: Real LLM provider using the OpenAI SDK already in deps.

**What's testable**: MockWebServer — no real API calls.

**Tests**:
- Request format: messages + tools serialized correctly
- Text response → LlmResponse(content=..., toolCalls=[])
- Tool call response → LlmResponse(toolCalls=[...])
- Streaming: chunks emitted in order
- HTTP 429/500 → error LlmResponse (no crash)
- Timeout → error after configured duration
- Cancellation → HTTP request cancelled

**Files**:
```
agent/llm/
└── OpenAiLlmProvider.kt
```

**Status**: Not Started

---

## Stage 13: TTS Providers

**Goal**: Swappable TTS — local PocketTTS, cloud OpenAI, Android built-in.

**What's testable**: Each provider behind same interface, MockWebServer for cloud.

**Tests**:
- PocketTtsProvider calls PocketTtsEngine.generate()
- OpenAiTtsProvider sends HTTP request (MockWebServer)
- AndroidTtsProvider calls TextToSpeech.speak()
- stop() cancels on all providers
- TtsProviderFactory returns correct impl from config string

**Files**:
```
agent/tts/
├── PocketTtsProvider.kt
├── OpenAiTtsProvider.kt
├── AndroidTtsProvider.kt
└── TtsProviderFactory.kt
```

**Status**: Not Started

---

## Stage 14: Accessibility Tools

**Goal**: AccessibilityService + screen reading/interaction tools.

**What's testable**: Mock node trees, tool interface tests.

**Tests**:
- ScreenReader.describeScreen(fakeTree) → natural language
- TapElementTool finds + clicks node
- OpenAppTool resolves package name
- TypeTextTool sets text on focused input
- ScrollTool scrolls scrollable nodes
- Tools with no service → clear error message
- Integration: AgentLoop + ReadScreenTool → full loop

**Files**:
```
accessibility/
├── LiveAIAccessibilityService.kt
├── ScreenReader.kt
└── AccessibilityBridge.kt

agent/tool/
├── ReadScreenTool.kt
├── TapElementTool.kt
├── OpenAppTool.kt
├── TypeTextTool.kt
├── ScrollTool.kt
└── ReadNotificationsTool.kt
```

**Status**: Not Started

---

## Stage 15: Shell + Binary Execution Tools

**Goal**: Shell commands and bundled native binaries as agent tools.

**What's testable**: Pure Kotlin (ShellExecutor, CommandWhitelist). Unit tests.

**Security**: Command whitelist, path sandboxing, timeout, output truncation.

**Tests**:
- Whitelisted command executes, returns stdout
- Blacklisted command → "Command not allowed" error
- Path traversal (../../etc/passwd) → "Path outside sandbox" error
- Timeout: slow command killed, error returned
- Output truncation at limit
- Exit code nonzero → stderr included
- Bundled binary resolves in nativeLibraryDir
- Missing binary → "Binary not found" error
- Integration: AgentLoop calls run_shell → result in memory

**Files**:
```
agent/
├── tool/
│   ├── ShellTool.kt
│   └── BinaryTool.kt
├── shell/
│   ├── ShellExecutor.kt
│   ├── CommandWhitelist.kt
│   └── BinaryRegistry.kt
```

**Status**: Not Started

---

## Stage 16: AppContainer + Settings Wiring

**Goal**: Connect Settings UI (Stage 6) to real provider factories.
API keys in EncryptedSharedPreferences. Provider hot-swap.

**What's testable**: DI wiring, key storage round-trip.

**Tests**:
- AppContainer "openai" → OpenAiLlmProvider
- AppContainer "fake" → FakeLlmProvider
- TtsProviderFactory returns correct impl
- API key store → retrieve → matches
- Provider change re-creates provider without restart

**Files**:
```
AppContainer.kt                     — MODIFIED: wire real providers
settings/
├── ApiKeyManager.kt               — NEW: EncryptedSharedPreferences
└── SettingsPanel.kt               — MODIFIED: wire to real config
```

**Status**: Not Started

---

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
    ├──► Stage 8:  Memory + Registry          ← pure Kotlin
    │         │
    │         ▼
    ├──► Stage 9:  AgentLoop + PauseGate      ← needs 7 + 8
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
              └──► Stage 15: Shell Tools      ← can parallel
                        │
                        ▼
                   Stage 16: AppContainer     ← final integration
```

### Parallelization Opportunities

```
After Stage 1:   Stages 2, 3, 4, 5, 6 ALL in parallel (UI work)
After Stage 7:   Stages 8, 9 sequential (dependency chain)
After Stage 11:  Stages 12, 13, 14, 15 ALL in parallel (providers + tools)
```

## Test Dependencies

```toml
# Add to libs.versions.toml
turbine = "1.2.0"
coroutines-test = "1.9.0"
mockwebserver = "4.12.0"

[libraries]
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
```

### Test Type Per Stage

```
Stage 1:  Pure JUnit                          — data classes, enums
Stage 2:  Compose UI tests (optional)         — @Preview + screenshot
Stage 3:  Compose UI tests (optional)         — @Preview + screenshot
Stage 4:  Compose UI tests (optional)         — @Preview + screenshot
Stage 5:  Compose UI tests (optional)         — @Preview + screenshot
Stage 6:  Compose UI tests (optional)         — @Preview + screenshot
Stage 7:  Pure JUnit                          — fakes
Stage 8:  Pure JUnit                          — memory + registry
Stage 9:  JUnit + Turbine + coroutines-test   — state machine
Stage 10: JUnit + Turbine + coroutines-test   — orchestrator + SCRUD
Stage 11: JUnit + Turbine                     — ViewModel integration
Stage 12: JUnit + MockWebServer               — HTTP round-trips
Stage 13: JUnit + MockWebServer + Robolectric — TTS providers
Stage 14: Instrumented or mock interface      — accessibility
Stage 15: Pure JUnit                          — shell executor
Stage 16: JUnit                               — DI wiring
```

## Message Format (OpenAI-Compatible)

All providers translate to/from this internal format:

```
┌─────────────────────────────────────────────────────┐
│ Message.System(content="You are a helpful...")      │
├─────────────────────────────────────────────────────┤
│ Message.User(content="Read my texts")               │
├─────────────────────────────────────────────────────┤
│ Message.Assistant(                                  │
│   content=null,                                     │
│   toolCalls=[                                       │
│     ToolCallRequest(                                │
│       id="call_abc",                                │
│       functionName="read_screen",                   │
│       argumentsJson="{}"                            │
│     )                                               │
│   ]                                                 │
│ )                                                   │
├─────────────────────────────────────────────────────┤
│ Message.ToolResult(                                 │
│   toolCallId="call_abc",                            │
│   content="WhatsApp - Chat: Mom (2 new)..."         │
│ )                                                   │
├─────────────────────────────────────────────────────┤
│ Message.Assistant(                                  │
│   content="You have 2 new texts from Mom..."        │
│ )                                                   │
└─────────────────────────────────────────────────────┘
```
