## Stage 20: Notification System (Blind-Optimized)

**Goal**: Notification channels and builders optimized for TalkBack. Blind users hear
notifications — `setTicker()` is the key field TalkBack reads aloud on arrival.
4 channels at different importance levels. Foreground service notification updates
without re-announcing.

**Channels**:

| Channel | Importance | TalkBack | Use |
|---------|-----------|----------|-----|
| `service_status` | LOW | Silent | Foreground service "I'm running" |
| `task_complete` | HIGH | Announces immediately | Task finished |
| `errors` | HIGH | Announces immediately | Errors requiring attention |
| `status_updates` | DEFAULT | Announces with tone | Informational updates |

**Key practices**:
- **Always `setTicker()` with a full sentence** — "Task complete: image described as a red car"
- **`setOnlyAlertOnce(true)`** on service notification — prevents TalkBack re-announcing on update
- **Action buttons with descriptive labels** — "Open result", "Dismiss", not just icons
- **Limit to 3 actions max** — cognitive load for ear-navigation
- **Group multiple task completions** with `setGroup()` and a summary notification
- **`setCategory()`** on every notification for system prioritization
- **Use `NotificationCompat.Builder`** (AndroidX), not framework `Notification.Builder`

**Tests**:
- Notification on task_complete channel has HIGH importance
- Notification has ticker text set
- Service notification uses setOnlyAlertOnce(true)
- Task completion notification has action buttons with labels
- Multiple task notifications grouped under summary
- Foreground notification updates without re-triggering TalkBack
- Notification with IMPORTANCE_HIGH triggers heads-up

**Files**:
```
agent/notification/
├── LiveAINotificationManager.kt   — channel creation, notification builders
└── NotificationChannels.kt        — channel ID constants + setup
```

**Status**: Not Started
