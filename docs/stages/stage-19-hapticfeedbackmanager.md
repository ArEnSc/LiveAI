## Stage 19: HapticFeedbackManager

**Goal**: Distinct vibration patterns for a blind user. Uses `Vibrator` API
(not `HapticFeedbackConstants`, since we run from a Service). `VibrationEffect.Composition`
on API 30+ with waveform fallback on older devices. Complements TalkBack — uses
multi-pulse patterns distinct from TalkBack's single-tick focus feedback.

**Haptic vocabulary**:

| Event | Pattern | Feel |
|-------|---------|------|
| `LISTENING_STARTED` | Single rising pulse, 150ms | "I'm awake" |
| `SPEECH_DETECTED` | Two quick taps, 80ms each | "I hear you" |
| `LISTENING_STOPPED` | Descending double-pulse | "Done listening" |
| `RESPONSE_READY` | Two medium pulses, wider gap | "About to speak" |
| `SUCCESS` | Rising triple (weak→medium→strong) | Positive completion |
| `ERROR` | Single long buzz, 400ms | Something wrong |
| `WARNING` | Three equal medium pulses | Pay attention |
| `TASK_COMPLETE` | Same as SUCCESS | Background task done |

**Anti-fatigue**: Debounce at 400ms minimum. Suppress during TTS playback.
Progressive reduction after 3rd same-type event in session.

**Permission**: `VIBRATE` (normal, auto-granted).

**Configurable**: Master toggle + intensity slider (LOW/MEDIUM/HIGH → amplitude scaling).

**Tests**:
- HapticFeedbackManager.perform(SUCCESS) → vibrator called with rising pattern
- HapticFeedbackManager.perform(ERROR) → vibrator called with long buzz
- Debounce: two perform() calls within 400ms → only first executes
- isEnabled=false → vibrator never called
- Intensity LOW → amplitudes scaled to 40%
- Suppress during TTS: isSpeaking=true → no vibration
- Works from Service context

**Files**:
```
agent/haptic/
├── HapticFeedbackManager.kt      — Vibrator API, pattern library, debounce
└── HapticEvent.kt                 — enum of all haptic events
```

**Status**: Not Started
