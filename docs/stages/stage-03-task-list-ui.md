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
