## Stage 11: Wire UI to Orchestrator + Persistence

**Goal**: Replace fake data in the UI with real OrchestratorState.
Connect ChatOverlayViewModel to Orchestrator. Mock responses → real agent loop.

Includes **conversation persistence**: Orchestrator rebuilds from Room on cold start.
Active conversation ID + provider config stored in Preferences DataStore (typed wrapper).
RUNNING tasks on kill → marked FAILED("interrupted"). QUEUED tasks re-enqueue.
TTS queue starts fresh (not persisted).

**What's visible**: Everything from Stages 2–6 now driven by real data.
Send a message → agent processes → response appears + TTS speaks.
Conversations survive app restarts.

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
- Cold start: messages loaded from Room, chat populated
- Cold start: RUNNING tasks marked FAILED("interrupted")
- Cold start: QUEUED tasks re-enqueued in TaskManager
- Active conversation ID persists across restart (DataStore)
- Provider config persists across restart (DataStore)

**Files**:
```
chat/
├── ChatOverlayViewModel.kt        — REWRITTEN: observe OrchestratorState
├── ChatOverlayManager.kt          — MODIFIED: inject Orchestrator

AppContainer.kt                     — NEW: composition root
LiveAIApplication.kt                — NEW: Application subclass
AppStateStore.kt                    — NEW: Preferences DataStore typed wrapper
```

**Status**: Not Started
