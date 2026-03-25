## Stage 18: NetworkMonitor

**Goal**: Real-time network connectivity monitoring via `ConnectivityManager.registerDefaultNetworkCallback`.
Exposes `StateFlow<NetworkState>` for reactive UI and agent logic. Detects "WiFi but no internet"
via `NET_CAPABILITY_VALIDATED`. Agent announces transitions via local TTS.

**Graceful degradation**: When offline, agent switches to local-only tools (accessibility,
shell, camera capture without vision, local TTS). Announces "I've lost internet. I can still
help with device controls and reading files." No request queuing — immediate feedback.

**Permission**: `ACCESS_NETWORK_STATE` (normal, auto-granted).

**Tests**:
- NetworkMonitor emits Online when VALIDATED capability present
- NetworkMonitor emits ConnectedNoInternet when INTERNET but not VALIDATED
- NetworkMonitor emits Offline when network lost
- Transition Online→Offline triggers announcement
- Transition Offline→Online triggers "back online" announcement
- currentNetworkState() returns correct initial state
- Works from Service context (not just Activity)

**Files**:
```
agent/network/
└── NetworkMonitor.kt              — ConnectivityManager → StateFlow<NetworkState>
```

**Status**: Not Started
