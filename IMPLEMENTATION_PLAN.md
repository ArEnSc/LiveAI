# Background Removal & GL State Guard

## Problem
Remove background image rendering from the wallpaper service and setup activity. After removal, the Live2D model rendered with missing pieces in the wallpaper service due to GL state leaking between rendering phases.

## What Was Done

### 1. Background Rendering Removal (Complete)
Removed from both `Live2DWallpaperService.kt` and `WallpaperSetupActivity.kt`:
- Background texture loading (`loadBackgroundWallpaper()` / `loadBackground()`)
- Background shader (`initBgShader()`, `compileShader()` in setup only)
- Background drawing (`drawBackground()`)
- All related fields (`bgTextureId`, `bgShaderProgram`, etc.)
- Unused imports (`BitmapFactory`, `GLUtils`, `File`, `ByteBuffer`/`ByteOrder` in setup, `LAppTextureManager`, `LAppDefine`)

Kept `compileShader()` in wallpaper service — needed by loading spinner.

### 2. GL State Guard (Complete)
**New file**: `app/src/main/java/com/example/liveai/live2d/GlStateGuard.kt`

Saves/restores full GLES20 state around `live2DManager?.onUpdate()`:
- Program, textures (units 0+1), buffers, vertex attribs 0-3
- Blend, depth/stencil/scissor, color mask, front face
- **FBO and viewport** (Cubism's `CubismRendererProfileAndroid.restore()` does NOT restore these)

Uses `ThreadLocal` for zero-allocation-per-frame. Kotlin `withGuard {}` + Java `getOrCreate()`.

**Integrated in:**
- `Live2DWallpaperService.kt` drawFrame() — both filter/no-filter paths
- `WallpaperSetupActivity.kt` SetupRenderer.onDrawFrame() — both paths
- `Live2DRenderer.java` onDrawFrame() — both paths

### 3. Dual-Engine Lifecycle Fix (Complete)
**Bug**: `forceReinitialize()` sets `refCount=1` unconditionally. When engine B takes over from engine A, refCount resets to 1. Engine A's `tearDown()` calls `release()`, refCount hits 0, framework gets disposed — crashing engine B with NPE on `CubismIdManager.getId()`.

**Fix in `Live2DWallpaperService.kt` `tearDown()`**: Only call `CubismLifecycleManager.release()` if `cubismGeneration == CubismLifecycleManager.generation`. If stale, skip — the new engine owns the lifecycle.

## Verification Results (API 36 Emulator)
- Setup activity: Model renders correctly, no missing pieces
- Wallpaper picker preview: Model renders correctly
- **Home screen wallpaper: Model renders correctly, 300+ frames, zero crashes**
- Lifecycle: Stale engine correctly skips `release()`, new engine survives

## Automated Test Steps (adb)

### Prerequisites
```bash
ADB=~/Library/Android/sdk/platform-tools/adb
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### Build & Install
```bash
./gradlew :app:installDebug
```

### Full Apply Flow
```bash
# Force stop and launch
$ADB shell am force-stop com.example.liveai
$ADB logcat -c
$ADB shell am start -n "com.example.liveai/.PermissionsActivity"
sleep 3

# Tap WALLPAPER SETTINGS — bounds [63,1397][1017,1523]
$ADB shell input tap 540 1460
sleep 20  # wait for model load

# Tap Apply Wallpaper — bounds [550,2281][1028,2358]
$ADB shell input tap 789 2320
sleep 5

# Tap Set wallpaper — bounds [386,2181][694,2316]
$ADB shell input tap 540 2249
sleep 3

# Tap "Home screen" — bounds [70,1070][1010,1196]
$ADB shell input tap 540 1133
sleep 10

# Go to home screen
$ADB shell input keyevent KEYCODE_HOME
sleep 10

# Verify: screenshot + logs
$ADB exec-out screencap -p > /tmp/wallpaper_test.png
$ADB logcat -d | grep "LiveAI-Wallpaper" | grep -E "drawFrame|failed"
# Expected: many drawFrame lines, zero "failed" lines
```

### Finding Button Coordinates (if they differ on your device)
```bash
$ADB shell uiautomator dump /sdcard/ui.xml
$ADB shell cat /sdcard/ui.xml | tr '>' '\n' | grep -i "wallpaper\|apply\|set.*wall\|home.*screen"
```

## Key Files

| File | What changed |
|------|-------------|
| `live2d/GlStateGuard.kt` | **NEW** — GL state save/restore utility |
| `Live2DWallpaperService.kt` | Removed bg rendering, added GlStateGuard, fixed lifecycle release |
| `WallpaperSetupActivity.kt` | Removed bg rendering, added GlStateGuard |
| `Live2DRenderer.java` | Added GlStateGuard |
| `live2d/CubismLifecycleManager.kt` | Reference only — not modified, but explains generation/refCount |
| `live2d/PostProcessFilter.java` | Reference only — FBO lifecycle for beginCapture/endCaptureAndApply |

## Status: Complete
