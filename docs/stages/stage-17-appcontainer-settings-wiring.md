## Stage 17: AppContainer + Settings Wiring

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
