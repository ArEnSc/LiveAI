## Stage 9: AgentLoop + PauseGate + SystemPromptBuilder + Error Recovery

**Goal**: The core agentic loop. Generate → tool calls → execute → loop.
Uses `ContextManager` (not raw memory) — calls `ingest()` for each message,
`assemble()` to build LLM context. PauseGate for cooperative suspend/resume.

Includes `SystemPromptBuilder` — template-based system prompt with variable injection.
Stored as `assets/system_prompt_v{N}.txt`, not inline in Kotlin. The AgentLoop
coordinates: builds system prompt, reserves its token budget, asks ContextManager
for remaining budget, combines both for the API call.

Includes error recovery: LlmProvider handles transport retries (429 backoff, 500 retry x3).
AgentLoop handles semantic recovery (auth errors → surface, partial streams → salvage if >20%).

**System prompt structure** (TTS-optimized for blind user):
```
1. IDENTITY — "You are a voice assistant for a blind Android user"
2. USER CONTEXT — {user_name}, {verbosity}, {preferences}
3. CAPABILITIES — available tools
4. CONSTRAINTS — no markdown, no visual references, short sentences
5. INTERACTION STYLE — announce before acting, confirm after, explain failures
```

**What's testable**: Turbine StateFlow tests with FakeLlmProvider + FakeContextManager.

**Tests**:
- Happy path: text response → states [Idle, Queued, Generating, Idle]
- Tool loop: toolCall then text → ExecutingTools, tool executed, provider called twice
- Multi-tool: 2 calls → both executed, both results ingested
- Max iterations: infinite tools → stops at limit
- Cancel during generate → Cancelled
- Cancel during tool exec → Cancelled
- Pause at gate → suspends, resume → continues from exact point
- contextManager.ingest() called for every message (user, assistant, tool result)
- contextManager.assemble() called before each generate()
- PauseGate unit: pause() then check() blocks; resume() unblocks
- SystemPromptBuilder: replaces {user_name} with actual name
- SystemPromptBuilder: includes available tool names
- SystemPromptBuilder: output under 1500 tokens
- System prompt token budget reserved before context assembly
- Error recovery: 429 → automatic retry with backoff (SDK built-in)
- Error recovery: 500 → retry 3x with exponential backoff
- Error recovery: 401 → surface immediately, no retry
- Error recovery: partial stream >20% → salvage partial content
- Error recovery: partial stream <20% → silent retry
- Error recovery: network unreachable → Error state with "offline" message

**Files**:
```
agent/
├── AgentLoop.kt
├── AgentLoopConfig.kt
├── PauseGate.kt
├── prompt/
│   ├── SystemPromptBuilder.kt       — template + variable injection
│   └── PromptVersion.kt             — version int, invalidates stale summaries
assets/
└── system_prompt_v1.txt              — core prompt template
```

**Status**: Not Started
