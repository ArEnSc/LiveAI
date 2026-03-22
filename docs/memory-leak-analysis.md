# Memory Leak Analysis: Wallpaper Setup Flow

**Date**: 2026-03-22
**Status**: Identified, not yet fixed
**Scope**: `WallpaperSetupActivity` setup → apply → wallpaper picker chain

---

## 1. Volume Handler Never Cancelled (Definite Leak)

**File**: `WallpaperSetupActivity.kt:577-586`

A `Handler` posts a repeating `Runnable` every 100ms to update the audio volume meter. It is never cancelled in `onPause()`, `onDestroy()`, or anywhere else.

**Reference chain**: `Handler` -> `Runnable` -> `audioVolumeSource` + `volumeMeter` + `volumeLabel` -> activity views -> activity

**Fix**: Store the `Handler` and `Runnable` as fields and call `volumeHandler.removeCallbacks(volumeUpdater)` in `onPause()` and `onDestroy()`.

---

## 2. Race Condition in `destroySessionOnGlThread()` (Potential Leak)

**File**: `WallpaperSetupActivity.kt:1176-1184`

```kotlin
session = null                      // 1. null out reference
glSurfaceView?.queueEvent { ... }   // 2. queue destroy on GL thread
glSurfaceView?.onPause()            // 3. stop GL thread
```

`onPause()` signals the GL thread to stop, but the queued `destroy` event may not execute before the thread pauses. In `onDestroy()`, `session` is already null so the fallback cleanup is skipped entirely. If the queued event didn't run, the Live2D model, textures, and audio source all leak.

**Fix**: Use a `CountDownLatch` to wait for the GL event to complete before calling `onPause()`, or restructure so the session reference is only nulled after confirmed destruction.

---

## 3. PostProcessFilter GL Resources Never Released (GL Leak)

**File**: `WallpaperSetupActivity.kt:1244` (init), no corresponding teardown

`postProcess.init()` allocates FBOs and textures. `resize()` deletes and recreates them internally. But there is no `destroy()`/`release()` method called when the activity finishes. The GL resources (FBO IDs, texture IDs) are orphaned.

**Fix**: Add a `PostProcessFilter.release()` method that calls `glDeleteFramebuffers` and `glDeleteTextures`, and invoke it on the GL thread during teardown (alongside session destruction).

---

## 4. View References Held After finish() (Minor)

**Fields**: `glSurfaceView`, `loadingOverlay`, `paramsContainer`, `paramListView`, `paramEditorView`

These nullable fields hold view references after `finish()` is called until GC collects the activity. In edge cases (e.g., configuration change during apply), they could prevent the old activity from being garbage collected promptly.

**Fix**: Null out view references in `onDestroy()`.

---

## Priority

| Issue | Severity | Effort |
|-------|----------|--------|
| Volume handler not cancelled | High | Low |
| Session destroy race condition | High | Medium |
| PostProcessFilter not released | Medium | Low |
| View references after finish | Low | Low |
