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
