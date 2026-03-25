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
