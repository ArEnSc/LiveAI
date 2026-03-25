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
