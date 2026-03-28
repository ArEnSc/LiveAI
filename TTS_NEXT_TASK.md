# Next Task: Producer-Consumer Audio Playback in PocketTtsProvider

## Problem

`PocketTtsProvider.streamGenerate()` writes audio frames directly to `AudioTrack.write()` inside the `onFrame` callback. This blocks the generation thread (~57ms/frame) because AudioTrack's small buffer fills up and `write()` stalls until playback drains. Generation computes at 3.5x realtime but wall-clock is throttled to 1.0x.

## Goal

Decouple generation from playback using a producer-consumer queue so generation runs at full compute speed and playback drains independently.

```
Generation thread ──[frames]──> Queue ──[frames]──> Playback thread → AudioTrack
   (runs at 3.5x)                                    (drains at 1.0x)
```

## File to Change

`app/src/main/java/com/example/liveai/agent/tts/PocketTtsProvider.kt`

## Implementation

### New Fields

- `private val frameQueue = LinkedBlockingQueue<ShortArray>()` — unbounded, ~170 frames max
- `private var playbackThread: Thread? = null`

### Changes to `streamGenerate()`

1. Create the AudioTrack (same as now)
2. Start a **playback drain thread** before calling `engine.generate()`:
   - Loop: `queue.take()` → if empty sentinel (`size == 0`) break → `track.write()` → update `totalSamplesWritten`
   - Start `track.play()` after first frame written (BUFFER_FRAMES_BEFORE_PLAY = 1)
   - After sentinel received: let AudioTrack drain remaining samples, then exit
3. In the `onFrame` callback (generation thread):
   - Sanitize in-place (already done)
   - Compute RMS for lip sync (keep on generation thread so lip sync tracks generation, not playback)
   - Convert to PCM16 using `pcm16Buffer`
   - **Copy** into a new `ShortArray` and enqueue (must copy since `pcm16Buffer` is reused)
   - Alternative: use a pool of `ShortArray` to avoid per-frame allocation
4. After `engine.generate()` returns:
   - Enqueue sentinel `ShortArray(0)` to signal drain thread
   - `playbackThread.join()` to wait for playback to finish
5. Remove `waitForPlaybackDrain()` — drain thread handles this

### Changes to `stop()`

```kotlin
override fun stop() {
    stopRequested.set(true)
    mouthVolume = 0f
    frameQueue.clear()              // drop queued frames immediately
    playbackThread?.interrupt()     // unblock queue.take() if waiting
    cleanupAudioTrack()
    speaking.set(false)
}
```

### Changes to `cleanupAudioTrack()`

Add: join/null the playback thread after stopping the track.

### ShortArray Pool (optional optimization)

To avoid allocating a new `ShortArray(1920)` per frame for the queue:

```kotlin
private val framePool = ArrayDeque<ShortArray>()

private fun acquireFrame(): ShortArray =
    framePool.removeLastOrNull() ?: ShortArray(PocketTtsEngine.SAMPLES_PER_FRAME)

private fun releaseFrame(frame: ShortArray) {
    framePool.addLast(frame)
}
```

The drain thread calls `releaseFrame()` after writing to AudioTrack. The onFrame callback calls `acquireFrame()` + copies pcm16Buffer into it + enqueues.

## Success Criteria

- Metrics RTFx reflects true compute speed (~3.5x) not wall-clock
- Audio plays smoothly without glitches
- `stop()` immediately silences playback (no queued frames leak through)
- Lip sync still works (mouthVolume updated on generation thread)
- `speak()` while previous is still playing: clean handoff, no thread leaks
- No thread leaks on `release()`

## Build & Verify

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
