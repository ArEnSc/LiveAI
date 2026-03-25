## Stage 8b: LosslessContextManager — DAG + Room

**Goal**: Port lossless-claw's hierarchical summarization DAG to Kotlin + Room.
Messages are never deleted (only redacted). Old messages compress into leaf
summaries, which condense into higher-level summaries. The agent can drill
back into any summary via expand(). Full-text search via Room FTS4.

**What's testable**: Room instrumented tests for DAG operations, pure JUnit
for compaction logic with fake DAOs.

### How the DAG Works

```
Raw messages → Leaf summaries (depth 0) → Condensed (depth 1) → Condensed (depth 2+)

Context window at any point:
  [Condensed D2] [Condensed D1] [Leaf] [Leaf] [msg] [msg] [msg] [msg]
   ◄── compressed old history ──────────────► ◄── fresh tail (protected) ►
```

**Compaction triggers** when context tokens exceed threshold (default 75% of budget):
1. Select oldest raw messages outside fresh tail
2. Summarize via LLM → create leaf node (depth 0)
3. Replace raw messages with leaf in context_items
4. When enough leaves accumulate (fanout, default 4) → condense into depth 1
5. Repeat upward as needed

**Redaction** marks messages as redacted, marks affected leaf summaries as stale.
Stale summaries are re-generated on next compaction cycle (lazy healing).

### Room Entities

```
┌──────────────┐     ┌──────────────────┐     ┌────────────────┐
│StoredMessage │     │    Summary       │     │  ContextItem   │
├──────────────┤     ├──────────────────┤     ├────────────────┤
│ id (PK)      │     │ id (PK)          │     │ id (PK)        │
│ conversationId│    │ conversationId   │     │ conversationId │
│ role          │     │ kind (leaf/cond) │     │ ordinal        │
│ content       │     │ depth            │     │ kind (msg/sum) │
│ tokenCount    │     │ content          │     │ referenceId    │
│ sequenceNum   │     │ tokenCount       │     └────────────────┘
│ createdAt     │     │ descendantCount  │
│ redacted      │     │ accumulatedTokens│     ┌────────────────┐
└──────┬───────┘     │ earliestAt       │     │SummaryMessage  │
       │              │ latestAt         │     ├────────────────┤
       │              │ stale            │     │ summaryId (FK) │
       │              │ createdAt        │     │ messageId (FK) │
       │              └────────┬─────────┘     └────────────────┘
       │                       │
       │              ┌────────┴─────────┐     ┌────────────────┐
       │              │  SummaryParent   │     │SummaryParent   │
       │              │  (DAG edges)     │     ├────────────────┤
       └──────────────┤                  │     │ parentId (FK)  │
                      └──────────────────┘     │ childId (FK)   │
                                               └────────────────┘
```

### Tests — DAG Operations

| Test | What It Proves |
|------|----------------|
| ingest 20 messages, compact(budget=10) → leaf summaries created | Leaf compaction works |
| leaves hit fanout(4) → condensed summary at depth 1 | Hierarchical condensation |
| assemble() returns summaries + fresh tail, under budget | Assembly respects limits |
| expand(leafId) → returns original source messages | Leaf expansion |
| expand(condensedId, depth=2) → walks full subtree | Deep expansion |
| search("Mom") → finds message via FTS4 | Full-text search |
| redactMessage(id) → excluded from expand, still in DB | Redaction doesn't delete |
| redact msg in leaf → leaf marked stale | Stale propagation |
| compact after redact → stale leaf re-summarized | Lazy DAG healing |
| correctSummary(id, new) → content updated, ancestors stale | Correction works |
| integrity() on healthy DAG → no errors | Validation passes |
| integrity() with orphan summary → reports error | Catches inconsistency |
| integrity() with broken ordinals → reports gap | Catches ordering issues |
| condense at depth 1, then condense at depth 2 → 3-level DAG | Multi-level works |
| assemble with 0 summaries → just returns raw messages | Graceful no-DAG case |
| stats() → correct counts for messages, summaries, depth, tokens saved | Stats accurate |

### Tests — CRUD via Tools

| Test | What It Proves |
|------|----------------|
| SearchHistoryTool("Mom") → contextManager.search called, results formatted | Tool→interface wiring |
| ExpandSummaryTool(id, depth=2) → contextManager.expand called | Expand tool works |
| ForgetMessagesTool("surprise party") → redactByQuery called | Delete tool works |
| ForgetBeforeTool("last Monday") → redactBefore called with correct timestamp | Date-based delete |
| ContextStatsTool → stats() called, formatted for speech | Stats tool works |

**Files**:
```
agent/
├── context/
│   └── lossless/
│       ├── LosslessContextManager.kt      — implementation, delegates to sub-components
│       │
│       ├── db/
│       │   ├── ContextDatabase.kt          — @Database(entities, version, DAOs)
│       │   ├── entity/
│       │   │   ├── StoredMessage.kt
│       │   │   ├── Summary.kt
│       │   │   ├── SummaryMessage.kt       — leaf → source messages junction
│       │   │   ├── SummaryParent.kt        — condensed → children DAG edges
│       │   │   └── ContextItem.kt          — ordered context window
│       │   └── dao/
│       │       ├── MessageDao.kt           — message CRUD + FTS search
│       │       ├── SummaryDao.kt           — summary CRUD + DAG queries
│       │       └── ContextItemDao.kt       — context window ordering
│       │
│       ├── compaction/
│       │   ├── Compactor.kt                — orchestrates leaf + condensation
│       │   ├── LeafCompactor.kt            — raw messages → leaf summary
│       │   ├── Condenser.kt                — sibling summaries → parent summary
│       │   └── CompactionConfig.kt         — thresholds, fanout, fresh tail size
│       │
│       ├── assembly/
│       │   └── ContextAssembler.kt         — mix summaries + messages, fit budget
│       │
│       ├── retrieval/
│       │   ├── ContextSearcher.kt          — FTS search, regex fallback
│       │   └── SummaryExpander.kt          — DAG traversal for expand()
│       │
│       ├── redaction/
│       │   └── Redactor.kt                 — mark redacted, stale ancestors
│       │
│       └── integrity/
│           └── IntegrityChecker.kt         — validate DAG + report
│
├── tool/
│   ├── SearchHistoryTool.kt                — search(query)
│   ├── ExpandSummaryTool.kt                — expand(id, depth)
│   ├── DescribeSummaryTool.kt              — describe(id)
│   ├── CorrectSummaryTool.kt               — correctSummary(id, content)
│   ├── ForgetMessagesTool.kt               — redactByQuery(query)
│   ├── ForgetBeforeTool.kt                 — redactBefore(timestamp)
│   └── ContextStatsTool.kt                 — stats()
```

**Status**: Not Started
