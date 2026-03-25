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
