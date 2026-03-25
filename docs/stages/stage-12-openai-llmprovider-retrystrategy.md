## Stage 12: OpenAI LlmProvider + RetryStrategy

**Goal**: Real LLM provider using the OpenAI SDK already in deps.
Includes `RetryStrategy` configuration: SDK handles 429 retries (5 max),
custom wrapper handles 500/502/503 (3 retries, exponential backoff with jitter).
Image attachment support for vision (camera tools in Stage 16).

**What's testable**: MockWebServer — no real API calls.

**Tests**:
- Request format: messages + tools serialized correctly
- Text response → LlmResponse(content=..., toolCalls=[])
- Tool call response → LlmResponse(toolCalls=[...])
- Streaming: chunks emitted in order
- HTTP 429 → SDK auto-retries (configured maxRetries=5)
- HTTP 500 → custom retry 3x with backoff, then error LlmResponse
- HTTP 401 → AuthenticationException surfaced immediately, no retry
- Timeout → error after configured duration
- Cancellation → HTTP request cancelled
- Image attachment: message with imageUri → base64 content block in request
- Partial stream failure: >20% received → partial content returned
- Partial stream failure: <20% received → full retry

**Files**:
```
agent/llm/
└── OpenAiLlmProvider.kt         — SDK config with RetryStrategy + server error wrapper
```

**Status**: Not Started
