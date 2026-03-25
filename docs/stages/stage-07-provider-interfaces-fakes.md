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
