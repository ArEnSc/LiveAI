# Live2D Touch Interaction — Implementation Plan

## Stage 1: Interaction Engine (Pure Kotlin Math)
**Goal**: Create the core computation layer — angle math, spring-back, intensity classification. Zero Android dependencies.
**Success Criteria**:
- `InteractionEngine.computeHeadAngles(deltaX, deltaY)` returns clamped angles
- `InteractionEngine.computeBodyAngles(deltaX, deltaY)` returns clamped angles
- `SpringAnimator` produces decaying oscillation values converging to zero
- `InteractionEngine.classifyIntensity(magnitude)` returns correct tier
**Tests**: Call functions with known inputs, verify output ranges and edge cases
**Files**:
- `interaction/InteractionEngine.kt` — pure math functions
- `interaction/SpringAnimator.kt` — time-based spring-back
- `interaction/InteractionModels.kt` — data classes (InteractionConfig, InteractionState, BodyPart)
**Status**: Complete

## Stage 2: Interaction Parameter Layer in LAppModel
**Goal**: Add a dedicated "interaction parameters" channel that applies angles to the model during active drag, separate from existing parameterOverrides.
**Success Criteria**:
- `LAppModel.setInteractionAngles("head", angleX, angleY)` drives ParamAngleX/Y during update()
- `LAppModel.clearInteractionAngles()` stops overriding (spring-back will use this with decaying values)
- Interaction angles apply BEFORE physics (so hair/clothes react)
- Existing parameter overrides and drag system still work when no interaction is active
**Tests**: Set interaction angles via debug UI slider or hardcoded test → model visibly tilts head/body
**Files**:
- `live2d/LAppModel.java` — add interaction angle support
- `live2d/LAppLive2DManager.java` — expose interaction methods
**Status**: Complete

## Stage 3: Touch Handling + Hit Zones in Wallpaper Service
**Goal**: Multi-touch pointer routing with simple rectangular hit zones. Drag on head → head tilts. Drag on body → body tilts.
**Success Criteria**:
- Touch on upper portion of model → head interaction (angles applied, model tilts)
- Touch on lower portion → body interaction (body angles applied)
- Touch outside model → falls through to existing behavior (random motion)
- Multiple fingers tracked independently
- Drag continues even if finger moves outside initial hit zone
**Tests**: Touch the wallpaper in different regions, verify correct angle response
**Files**:
- `interaction/TouchInteractionHandler.kt` — pointer routing + hit detection
- `Live2DWallpaperService.kt` — wire into onTouchEvent
- `live2d/LAppLive2DManager.java` — expose model bounds for hit zone computation
**Status**: Complete (includes spring-back from Stage 4)

## Stage 4: Spring-Back Animation on Release
**Goal**: When finger lifts, angles animate back to zero with a bouncy elastic feel.
**Success Criteria**:
- Release after head drag → head bounces back to neutral over ~1 second
- Release after body drag → body bounces back over ~1.2 seconds
- Spring animation feeds through physics (hair/clothes react to bounce)
- New touch during spring cancels the spring and starts fresh drag
**Tests**: Drag and release head/body, verify visible bounce-back
**Files**:
- `interaction/SpringAnimator.kt` — already created in Stage 1, now wired to frame loop
- `interaction/TouchInteractionHandler.kt` — manage spring lifecycle
**Status**: Complete (wired in Stage 3)

## Stage 5: Eye Control During Head Pat
**Goal**: Eyes close proportionally when dragging down on head, reopen on release.
**Success Criteria**:
- Drag down on head → eyes progressively close (angleY = -30 → fully closed)
- Drag up on head → eyes stay open
- Release → eyes animate open over 200ms
- Auto-blink disabled during head interaction, resumes after
**Tests**: Drag head downward, verify eyes close. Release, verify eyes open smoothly.
**Files**:
- `interaction/EyeController.kt` — manual eye override + animated return
- `live2d/LAppModel.java` — method to override eye openness (bypass CubismEyeBlink)
**Status**: Not Started

## Stage 6: Configuration UI + Persistence
**Goal**: Add hit zone configuration to the Interaction tab in WallpaperSetupActivity. Persist body part mappings.
**Success Criteria**:
- User can adjust head/body hit zone positions and sizes via sliders
- Hit zones persist across wallpaper restarts via SharedPreferences
- Sensitivity sliders for head and body interaction
- Visual indicator of hit zones in setup activity (optional debug overlay)
**Tests**: Adjust hit zones in setup, set wallpaper, verify zones match configuration
**Files**:
- `WallpaperSetupActivity.kt` — add UI controls to Interaction tab
- `interaction/InteractionConfig.kt` — persistence via SharedPreferences
**Status**: Not Started
