# Stream Session Lifecycle & Concurrency Engineering (Milestones 55 & 56)

## 1. Concurrency Risks in Network Streaming

Network playback involves asynchronous, multi-threaded pipelines where network I/O, demuxing, buffering, and decoding operate concurrently. In a dynamic media player, user interactions create race conditions:
1. **Rapid Source Switching**: User opens Stream A, which starts buffering over a slow network. User quickly selects Stream B. Without isolation, delayed callbacks or error events from Stream A may arrive and overwrite the state of Stream B or start rendering frames from Stream A.
2. **Stop During Reconnection / Buffering**: User hits Stop while a network retry or buffering phase is active. If recovery coroutines are not cancelled, the player might reconnect in the background and abruptly resume playing audio.
3. **Pause During Buffer Underrun**: User hits Pause while the player is paused-for-cache (buffering). When the cache fills up, the engine must remain in `Paused` status rather than transitioning to `Playing`.

---

## 2. Session Generation Tokens (`sessionGeneration`)

To ensure complete isolation between consecutive playback attempts without tearing down the native engine, `MpvPlayerEngine` employs an atomic generation counter:

```kotlin
private var sessionGeneration = 0L
```

### Protocol:
1. **On `open(media)`**:
   - `sessionGeneration` is incremented: `val currentSession = ++sessionGeneration`.
   - `userStopped = false` and `userPaused = false` are set.
   - Polling coroutines and property observers receive `expectedSession = currentSession`.
   - Polling loops check:
     ```kotlin
     while (isActive && !closed && sessionGeneration == expectedSession) {
         // Process demuxer properties
         if (sessionGeneration != expectedSession) break
         mutableState.value = ...
     }
     ```
2. **On `stop()`**:
   - `userStopped = true` and `userPaused = false` are set.
   - `sessionGeneration++` invalidates any running background callbacks.
   - `polling?.cancel()` halts active status monitoring.
   - Native player executes `command("stop")`.
   - Player state is synchronously reset to `PlaybackStatus.Idle`.

---

## 3. Preserving User Stop & Pause Intent

- **Stop Preservation**: Explicit user invocation of `stop()` sets `userStopped = true` and resets state to `PlaybackStatus.Idle`. If a network retry timer fires or an async buffer event occurs, `policy.shouldRetry(..., isUserCancelled = true)` halts reconnection immediately.
- **Pause Preservation**: Explicit user invocation of `pause()` sets `userPaused = true`. The status computation evaluates:
  ```kotlin
  status = when {
      idle && duration != null && position >= duration - 500 -> PlaybackStatus.Completed
      idle -> PlaybackStatus.Ready
      pausedForCache -> PlaybackStatus.Buffering
      paused || userPaused -> PlaybackStatus.Paused
      else -> PlaybackStatus.Playing
  }
  ```
  Even if `paused-for-cache` clears or native `pause` property transiently reports `false` during seek or readahead, `userPaused` ensures the user's intent to remain paused is honored.

---

## 4. Resource Cleanup & Memory Safety

- When switching media items, active demuxer caches are flushed (`loadfile media.uri replace`).
- Video render contexts are retained across tracks and streams to prevent expensive surface recreation and flicker, while audio enhancement filters and subtitles are reset.
- Native resources are finalized deterministically in `close()`: polling is cancelled, renderers detached, `mpv_terminate_destroy()` executed, and coroutine scopes cancelled.
