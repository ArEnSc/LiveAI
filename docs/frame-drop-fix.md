# Frame Drop Investigation: Touch-Driven Layout Throttling

**Status**: ABANDONED — changes reverted. Documented here for future reference.

## Problem

The live wallpaper drops frames whenever touch is applied — either dragging the chat overlay ball or interacting with the Live2D model's touch zones. The app targets 60fps (16ms frame budget) and touch events blow past that budget.

## Root Causes Identified

### 1. Synchronous WindowManager IPC on every MOVE event (ChatOverlayManager)

`windowManager.updateViewLayout()` is a synchronous Binder IPC call. It fires on **every** `ACTION_MOVE` during chat ball drag — up to 120Hz+ on modern devices. When the panel is expanded, it doubles (one for tab, one for panel).

**File**: `ChatOverlayManager.kt`, `setupTouchHandling()`

### 2. Live2D parameter updates on every MOVE event (TouchInteractionHandler)

`target.setInteractionParams()` fires on every `ACTION_MOVE` in `handlePointerMove()`. This updates Cubism SDK model parameters and can trigger mesh deformation recomputation. Most updates are immediately overwritten by the next MOVE before a frame renders.

**File**: `TouchInteractionHandler.kt`, `handlePointerMove()`

### 3. Render loop drift with postDelayed(16)

`handler.postDelayed(drawRunnable, 16L)` causes drift — if `drawFrame()` takes 10ms, the next frame starts at 26ms, not the next vsync. Touch event bursts in the message queue can further delay the draw callback.

**File**: `Live2DWallpaperService.kt`, `drawRunnable`

## Attempted Fixes

### Fix 1: Choreographer-throttled overlay drag (ChatOverlayManager)

Deferred `updateViewLayout()` to next vsync via `Choreographer.postFrameCallback()`. Params updated in-memory on MOVE, actual IPC batched to once per frame.

**Result**: Chat overlay drag became smooth. No crash.

### Fix 2: Per-frame drag param batching (TouchInteractionHandler)

`handlePointerMove()` stored values in `pendingDragParams` instead of calling `setInteractionParams()`. New `updateFrame()` method applied params once per render frame.

**Result**: First attempt crashed — likely a Choreographer threading issue (eager `Choreographer.getInstance()` at field init). Second attempt with `by lazy` didn't crash but the wallpaper froze when dragging interaction zones. The batching may have introduced a timing gap where params weren't applied when the model expected them, or the interaction between deferred params and the spring animator had an edge case.

### Fix 3: Choreographer-synced render loop (Live2DWallpaperService)

Replaced `handler.postDelayed(drawRunnable, 16L)` with `Choreographer.postFrameCallback()`.

**Result**: Wallpaper froze. Choreographer frame callbacks don't work well with WallpaperService's EGL rendering model. The `postDelayed` approach, while drifty, works because it keeps the draw loop independent of the vsync signal — important since the wallpaper engine manages its own EGL context and swap timing. Reverting to Handler fixed the freeze.

## Lessons Learned

1. **Don't use Choreographer in WallpaperService** — the EGL context management doesn't align with Choreographer's vsync delivery. The Handler + postDelayed(16) pattern is the standard approach for wallpaper engines.

2. **Eager `Choreographer.getInstance()` crashes in inner classes** — use `by lazy` to ensure it's called on a Looper thread at first access, not during construction.

3. **Batching touch params needs more investigation** — the simple "store and flush" approach may drop params or create timing gaps with the spring animator. The interaction between `pendingDragParams`, `updateFrame()`, and the existing spring system needs more careful state machine design.

4. **The overlay Choreographer fix worked in isolation** — the ChatOverlayManager throttling was confirmed smooth. If frame drops are primarily from overlay drag, this fix alone could be re-applied independently.

## Future Approaches to Consider

- **Throttle MOVE events by timestamp** instead of Choreographer — simple `SystemClock.uptimeMillis()` check in `handlePointerMove()` to skip updates within 16ms of the last one. No Choreographer needed.
- **Move Live2D parameter updates off the main thread** — process MOVE events on main thread but apply params on a dedicated render thread.
- **Use `MotionEvent.getHistoricalSize()`** to coalesce batched events instead of processing each one.
- **Profile with systrace/perfetto** to confirm whether the bottleneck is actually the `setInteractionParams()` call or something else in the rendering pipeline.
