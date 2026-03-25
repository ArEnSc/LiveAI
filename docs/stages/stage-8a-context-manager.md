## Stage 8a: ContextManager Interface + Simple/Fake + ToolRegistry + TokenCounter

**Goal**: Define the `ContextManager` interface with full CRUD (ingest, assemble,
search, expand, correct, redact). Implement `SimpleContextManager` (in-memory list)
and `FakeContextManager` (scripted for tests). Also `ToolRegistry` for tool dispatch.
Also `TokenCounter` interface — needed by ContextManager for budget enforcement.

**Token counting**: Use `ktoken` (by same author as OpenAI Kotlin SDK) for exact
OpenAI counts. `HeuristicTokenCounter` (`text.length / 3.5 + 20% buffer`) as fallback.
The API's `usage` response field provides exact post-hoc counts for free.

**What's testable**: Pure unit tests, no Android deps.

### ContextManager Interface

```kotlin
interface ContextManager {
    // Create
    suspend fun ingest(message: Message)
    suspend fun ingestBatch(messages: List<Message>)

    // Read
    suspend fun assemble(tokenBudget: Int): List<Message>
    suspend fun search(query: String, scope: SearchScope = SearchScope.ALL): List<SearchResult>
    suspend fun describe(summaryId: String): SummaryInfo?
    suspend fun expand(summaryId: String, maxDepth: Int = 1, tokenBudget: Int = 4000): List<Message>

    // Update
    suspend fun correctSummary(summaryId: String, correctedContent: String): Boolean
    suspend fun recompact(summaryId: String): Boolean

    // Delete
    suspend fun redactMessage(messageId: String): Boolean
    suspend fun redactMessages(messageIds: List<String>): Int
    suspend fun redactByQuery(query: String): Int
    suspend fun redactConversation(conversationId: String): Boolean
    suspend fun redactBefore(timestamp: Long): Int
    suspend fun clearAll(): Boolean

    // Compaction
    suspend fun compact(tokenBudget: Int)

    // Lifecycle
    suspend fun stats(): ContextStats
    suspend fun integrity(): IntegrityReport
}
```

**Tests**:
- SimpleContextManager: ingest 10, assemble returns 10
- SimpleContextManager: ingest 20 with budget for 10 → oldest dropped, system preserved
- SimpleContextManager: search("Mom") → finds matching messages
- SimpleContextManager: redactMessage(id) → message gone from assemble
- SimpleContextManager: redactBefore(timestamp) → older messages removed
- SimpleContextManager: clearAll → empty
- SimpleContextManager: stats() returns correct counts
- FakeContextManager: assemble returns canned list
- FakeContextManager: search returns canned results
- Registry: register + execute returns result
- Registry: unknown tool → error string (no crash)
- Registry: getDefinitions() returns schemas for all tools
- EchoTool: returns its input as output
- KTokenCounter: counts tokens for "Hello world" and matches expected
- HeuristicTokenCounter: estimates within 20% of KTokenCounter for English text
- TokenCounter with empty string returns 0

**Files**:
```
agent/
├── context/
│   ├── ContextManager.kt              — interface (full CRUD)
│   ├── SearchScope.kt
│   ├── SearchResult.kt
│   ├── SummaryInfo.kt
│   ├── ContextStats.kt
│   ├── IntegrityReport.kt
│   ├── simple/
│   │   └── SimpleContextManager.kt    — List<Message>, truncate, linear search
│   └── fake/
│       └── FakeContextManager.kt      — scripted responses for tests
├── token/
│   ├── TokenCounter.kt                — interface { fun count(text: String): Int }
│   ├── KTokenCounter.kt              — ktoken, exact for OpenAI
│   └── HeuristicTokenCounter.kt      — text.length / 3.5 + buffer
├── tool/
│   ├── Tool.kt
│   ├── ToolRegistry.kt
│   └── EchoTool.kt
```

**Status**: Not Started
