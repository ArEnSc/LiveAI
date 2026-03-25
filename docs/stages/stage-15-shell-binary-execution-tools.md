## Stage 15: Shell + Binary Execution Tools

**Goal**: Shell commands and bundled native binaries as agent tools.

**What's testable**: Pure Kotlin (ShellExecutor, CommandWhitelist). Unit tests.

**Security**: Command whitelist, path sandboxing, timeout, output truncation.

**Tests**:
- Whitelisted command executes, returns stdout
- Blacklisted command → "Command not allowed" error
- Path traversal (../../etc/passwd) → "Path outside sandbox" error
- Timeout: slow command killed, error returned
- Output truncation at limit
- Exit code nonzero → stderr included
- Bundled binary resolves in nativeLibraryDir
- Missing binary → "Binary not found" error
- Integration: AgentLoop calls run_shell → result in memory

**Files**:
```
agent/
├── tool/
│   ├── ShellTool.kt
│   └── BinaryTool.kt
├── shell/
│   ├── ShellExecutor.kt
│   ├── CommandWhitelist.kt
│   └── BinaryRegistry.kt
```

**Status**: Not Started
