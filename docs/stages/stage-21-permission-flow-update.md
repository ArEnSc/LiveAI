## Stage 21: Permission Flow Update

**Goal**: Update `PermissionsActivity` for progressive/contextual permission requests.
Not all permissions upfront — request each when the feature is first used.
TalkBack-friendly rationale screens. Handle "Don't ask again" with Settings redirect.

**Permission flow**:

| Permission | When Requested | Why |
|-----------|---------------|-----|
| `POST_NOTIFICATIONS` | First launch (Android 13+) | Foreground service notification |
| `SYSTEM_ALERT_WINDOW` | First overlay enable | Draw over other apps |
| `RECORD_AUDIO` | First push-to-talk use | Speech recognition |
| `CAMERA` | First vision tool use | Photo capture |
| Accessibility Service | First screen-reading tool use | Read/interact with other apps |

**Key practices**:
- Pre-permission rationale screen with TalkBack-friendly `contentDescription`
- Track "has asked before" in SharedPreferences (distinguish first-ask from permanently-denied)
- `shouldShowRequestPermissionRationale()` = false + has-asked = true → redirect to Settings
- Check permissions on every service start AND at point of use
- Accessibility Service: use `Settings.ACTION_ACCESSIBILITY_SETTINGS` intent, check via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`
- Use Activity Result API directly (Accompanist is deprecated)

**Tests**:
- Permission granted → feature proceeds
- Permission denied (first time) → rationale shown
- Permission denied with "Don't ask again" → Settings redirect offered
- Accessibility not enabled → tools return clear error message
- Service start with missing permissions → graceful degradation, notification prompts
- All permissions revoked while backgrounded → detected on next use

**Files**:
```
PermissionsActivity.kt              — MODIFIED: progressive flow, TalkBack-friendly rationales
```

**Status**: Not Started
