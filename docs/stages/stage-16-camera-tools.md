## Stage 16: Camera Tools

**Goal**: CameraX-based photo capture as agent tools. No preview needed —
snap silently from the service, send to vision LLM, speak description.
Essential for a blind user: "What's in front of me?", "Read this label."

**How it works**: CameraX `ImageCapture` use case bound without a Preview surface.
Works from a Service with a `LifecycleOwner` (ChatOverlayManager already implements this).

**Permission**: `android.permission.CAMERA` (runtime, same pattern as RECORD_AUDIO).

**Tools**:

| Tool | Voice Command | What Happens |
|------|--------------|--------------|
| `take_photo` | "What's in front of me?" | Back camera → vision LLM → spoken description |
| `take_selfie` | "How do I look?" | Front camera → vision LLM → spoken description |
| `read_text` | "Read this label" | Back camera → vision LLM "extract text" → spoken |
| `describe_scene` | "Describe the room" | Back camera → vision LLM scene prompt → spoken |

**Data model change** — image support in messages:
```kotlin
// Message.User gains optional image attachment
data class User(
    ...
    val imageUri: String? = null        // photo for vision LLM
) : Message
```

**Success Criteria**:
- `CameraManager` captures photo without preview surface
- Front/back camera selectable
- Photo saved to app cache, URI returned
- `TakePhotoTool.execute({"camera":"back"})` → captures + returns URI
- Vision-capable LlmProvider sends image with message (OpenAI/Anthropic both support this)
- Flash/torch controllable
- Integration: "What's in front of me?" → snap → vision LLM → TTS speaks description

**Tests**:
- CameraManager.capture(BACK) returns valid file URI (instrumented test)
- CameraManager.capture(FRONT) returns valid file URI
- TakePhotoTool with no camera permission → clear error message
- TakePhotoTool result includes file path in tool output
- LlmProvider.generate() with imageUri attaches image content (MockWebServer verifies multipart/base64)
- Integration: AgentLoop + FakeLlmProvider + TakePhotoTool → photo taken, result in memory

**Files**:
```
agent/
├── tool/
│   ├── TakePhotoTool.kt            — snap photo via CameraManager
│   ├── ReadTextTool.kt             — snap + "extract text" vision prompt
│   └── DescribeSceneTool.kt        — snap + scene description prompt
├── camera/
│   └── CameraManager.kt            — CameraX ImageCapture, no preview
```

**Status**: Not Started
