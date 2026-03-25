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
