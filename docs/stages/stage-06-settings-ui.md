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
