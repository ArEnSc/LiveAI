# Per-Context Cubism Rendering — Implementation Plan

## Problem

We have 3 independent EGL contexts (wallpaper engine, setup activity, overlay service) that each need to render a Live2D model. The current code treats CubismFramework as something only one context can own at a time, using `forceReinitialize` / `dispose` / `cleanUp` to hand off between them. This handoff cycle is what causes all our rendering bugs — races, stale shaders, NPEs on `CubismIdManager`, and missing model pieces.

## Root Cause

**We caused the problem ourselves.** The Cubism SDK's `CubismFramework.initialize()` does not touch GL at all — it just creates a `CubismIdManager` (a string→ID map). The framework is happy to have multiple models loaded simultaneously. The **only** GL-bound static state is:

```
CubismShaderAndroid.s_instance  (one static field)
```

This singleton holds compiled shader program IDs. Shader program IDs are EGL-context-local — program ID 2 on context A is meaningless on context B. When context A compiles shaders into `s_instance`, then context B tries to use those same program IDs, it draws garbage or crashes.

Our `forceReinitialize` "fix" makes it worse: it calls `dispose()` which nukes the ID manager and shaders, then reinitializes on the new context — breaking the old context mid-draw.

## Solution

Make `CubismShaderAndroid` per-GL-thread via `ThreadLocal`. Each EGL context compiles its own shaders on its own GL thread. No handoffs, no locks, no generation counters. Each screen independently loads its model and renders.

### What changes in the framework

**One file, one line**: `CubismShaderAndroid.java`

```java
// BEFORE (broken across contexts)
private static CubismShaderAndroid s_instance;

// AFTER (each GL thread gets its own)
private static final ThreadLocal<CubismShaderAndroid> s_threadInstance = new ThreadLocal<>();
```

Update `getInstance()`, `deleteInstance()`, and `doStaticRelease()` to use the ThreadLocal.

### What changes in app code

- **Delete** `CubismLifecycleManager.kt` — no longer needed
- **Delete** `forceReinitialize`, `frameworkLock`, `generation`, `refCount` — all gone
- Each screen calls `CubismFramework.startUp()` + `initialize()` once (idempotent)
- Each screen loads its own model and renders independently
- Each screen cleans up its own model on destroy (no need to touch framework)

---

## Stage 1: ThreadLocal Shader Singleton

**Goal**: Make `CubismShaderAndroid` per-GL-thread so shader programs are EGL-context-local.

**File**: `framework/src/main/java/com/live2d/sdk/cubism/framework/rendering/android/CubismShaderAndroid.java`

### Changes

1. Replace `private static CubismShaderAndroid s_instance` with `private static final ThreadLocal<CubismShaderAndroid> s_threadInstance = new ThreadLocal<>()`
2. Update `getInstance()`:
   ```java
   public static CubismShaderAndroid getInstance() {
       CubismShaderAndroid instance = s_threadInstance.get();
       if (instance == null) {
           instance = new CubismShaderAndroid();
           s_threadInstance.set(instance);
       }
       return instance;
   }
   ```
3. Update `deleteInstance()`:
   ```java
   public static void deleteInstance() {
       s_threadInstance.remove();
   }
   ```

**File**: `framework/src/main/java/com/live2d/sdk/cubism/framework/rendering/android/CubismRendererAndroid.java`

4. `doStaticRelease()` already calls `CubismShaderAndroid.deleteInstance()` — now only cleans up the calling thread's shaders (correct behavior).

**Success Criteria**:
- [ ] Each GL thread compiles its own shader programs
- [ ] Deleting shaders on one thread does not affect another
- [ ] Framework compiles, no API changes to callers of `getInstance()`

**Status**: Complete

---

## Stage 2: One-Time Framework Init

**Goal**: Call `CubismFramework.startUp()` + `initialize()` once at app startup. Never call `dispose()` / `cleanUp()` / `forceReinitialize` again.

**File**: `app/src/main/java/com/example/liveai/live2d/CubismLifecycleManager.kt` → simplify to:

```kotlin
object CubismLifecycleManager {
    private const val TAG = "CubismLifecycle"
    private var initialized = false
    private val lock = Any()

    /** Call from Application.onCreate() or first consumer. Idempotent. */
    fun ensureStarted(context: Context) {
        synchronized(lock) {
            if (initialized) return
            LAppPal.setup(context.applicationContext)
            val option = CubismFramework.Option()
            option.logFunction = LAppPal.PrintLogFunction()
            option.loggingLevel = LAppDefine.cubismLoggingLevel
            CubismFramework.startUp(option)
            CubismFramework.initialize()
            initialized = true
            Log.d(TAG, "CubismFramework started and initialized (once)")
        }
    }
}
```

**What gets deleted**:
- `acquire()` / `release()` / `forceReinitialize()` — gone
- `frameworkLock` — gone
- `generation` counter — gone
- `refCount` — gone

**Success Criteria**:
- [ ] `CubismFramework.initialize()` called exactly once per app process
- [ ] No `dispose()` or `cleanUp()` calls anywhere
- [ ] `CubismIdManager` stays alive for the entire process

**Status**: Complete

---

## Stage 3: Independent Rendering in Each Screen

**Goal**: Each screen loads its own model on its own GL thread, renders independently, cleans up its own resources.

### 3a. Live2DWallpaperService.kt

**Remove**:
- `CubismLifecycleManager.acquire/release/forceReinitialize` calls
- `frameworkLock.readLock()` in `drawFrame()`
- `cubismGeneration` field and all generation checks
- `cubismAcquired` field

**Add**:
- `CubismLifecycleManager.ensureStarted(context)` in `setupEverything()` (before model load)
- Model loads on wallpaper's GL thread as before
- `drawFrame()` just draws — no lock, no generation check
- `tearDown()` releases model/textures only (no framework dispose)

### 3b. WallpaperSetupActivity.kt

**Remove**:
- `CubismLifecycleManager.acquire/release/forceReinitialize` calls
- `cubismGeneration` field
- `Live2DSessionFactory` / `Live2DSession` usage (if only used for lifecycle tracking)

**Add**:
- `CubismLifecycleManager.ensureStarted(context)` in `SetupRenderer.onSurfaceCreated()`
- Load model on GLSurfaceView's GL thread
- `onDestroy()` releases model/textures via `queueEvent`

### 3c. OverlayService.kt

**Remove**:
- `CubismLifecycleManager.acquire/release/forceReinitialize` calls

**Add**:
- `CubismLifecycleManager.ensureStarted(context)` in renderer's `onSurfaceCreated()`
- Load model on GLSurfaceView's GL thread
- `onDestroy()` releases model/textures

**Success Criteria**:
- [ ] Wallpaper renders model on home screen
- [ ] Setup activity renders model simultaneously (if both visible)
- [ ] Overlay renders model independently
- [ ] Opening setup does NOT break wallpaper
- [ ] Closing setup does NOT require wallpaper to reinitialize
- [ ] No crashes during any transition

**Tests**:
- adb smoke: launch wallpaper → open setup → close setup → wallpaper still rendering
- adb smoke: open setup while wallpaper running → both work
- adb smoke: full apply flow (setup → apply → home screen)
- adb smoke: 3 rounds of home → setup → close → home, zero crashes

**Status**: Complete

---

## Stage 4: Cleanup Dead Code

**Goal**: Remove all vestiges of the old ownership/lifecycle system.

### Delete
- `EXCLUSIVE_CUBISM_OWNERSHIP_PLAN.md`
- `Live2DSession.kt` / `Live2DSessionFactory.kt` (if no longer needed)
- Any `frameworkLock` references in comments
- Any `generation` references in comments

### Simplify
- `GlStateGuard.kt` — keep as-is (still needed, Cubism doesn't restore FBO/viewport)
- `CubismLifecycleManager.kt` — now just 15 lines

**Success Criteria**:
- [ ] No references to `forceReinitialize`, `frameworkLock`, `generation`, `refCount`
- [ ] Project compiles clean
- [ ] All smoke tests pass

**Status**: Complete

---

## Why This Works

| Question | Answer |
|----------|--------|
| **Can CubismFramework handle multiple models?** | Yes. Models are instances, framework is just an ID manager. |
| **What about `CubismIdManager`?** | It's a string→ID map with no GL state. Thread-safe for reads. Shared across all contexts — fine. |
| **What about model textures?** | Each model loads its own textures via `glGenTextures` on its own GL thread. Texture IDs are per-EGL-context. No conflict. |
| **What about the clipping mask FBO?** | Each `CubismRendererAndroid` instance creates its own `CubismOffscreenSurfaceAndroid`. Per-instance, not static. No conflict. |
| **What about `LAppPal.updateTime()`?** | Uses `System.nanoTime()`. No GL state. Fine to call from any thread. |
| **What breaks if two threads call `getInstance()` simultaneously?** | Nothing — `ThreadLocal` guarantees each thread gets its own instance. |

## Risk: Memory

Each GL context compiles ~7 unique shader programs (19 slots, but 7-12 and 13-18 reuse 1-6). That's ~7 shader programs × 3 contexts = ~21 programs. Trivial GPU memory cost.

Each context also loads its own copy of model textures. For a single model this is typically 1-4 textures at maybe 2-4 MB each. So worst case ~12 MB extra GPU memory when all 3 contexts are active simultaneously. Acceptable.

## File Map

| File | Stage | Action |
|------|-------|--------|
| `framework/.../CubismShaderAndroid.java` | 1 | Modify — `s_instance` → `ThreadLocal` |
| `framework/.../CubismRendererAndroid.java` | 1 | No change needed (already delegates to `deleteInstance()`) |
| `live2d/CubismLifecycleManager.kt` | 2 | Rewrite — simplify to `ensureStarted()` only |
| `Live2DWallpaperService.kt` | 3 | Modify — remove lifecycle/lock code, use `ensureStarted()` |
| `WallpaperSetupActivity.kt` | 3 | Modify — remove lifecycle/lock code, use `ensureStarted()` |
| `OverlayService.kt` | 3 | Modify — remove lifecycle/lock code, use `ensureStarted()` |
| `live2d/GlStateGuard.kt` | — | No change |
| `EXCLUSIVE_CUBISM_OWNERSHIP_PLAN.md` | 4 | Delete |
