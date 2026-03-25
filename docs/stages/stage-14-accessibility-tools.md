## Stage 14: Accessibility Tools

**Goal**: AccessibilityService + screen reading/interaction tools.

**What's testable**: Mock node trees, tool interface tests.

**Tests**:
- ScreenReader.describeScreen(fakeTree) → natural language
- TapElementTool finds + clicks node
- OpenAppTool resolves package name
- TypeTextTool sets text on focused input
- ScrollTool scrolls scrollable nodes
- Tools with no service → clear error message
- Integration: AgentLoop + ReadScreenTool → full loop

**Files**:
```
accessibility/
├── LiveAIAccessibilityService.kt
├── ScreenReader.kt
└── AccessibilityBridge.kt

agent/tool/
├── ReadScreenTool.kt
├── TapElementTool.kt
├── OpenAppTool.kt
├── TypeTextTool.kt
├── ScrollTool.kt
└── ReadNotificationsTool.kt
```

**Status**: Not Started
