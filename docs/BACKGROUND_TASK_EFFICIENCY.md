# Vimivo — Background Task & Coroutine Optimization

> [!NOTE]
> Authoritative performance and lifecycle documentation detailing background execution, coroutine scopes, priority hierarchies, and mobile battery compliance across **Milestones 63 and 64**.

---

## 1. Executive Summary

Vimivo enforces strict background execution constraints to eliminate unnecessary CPU wakeups, battery drain, and memory leaks. Background processing strictly adheres to platform operating system guidelines across Android, iOS, macOS, Windows, and Linux.

---

## 2. Task Priority Hierarchy

All application workloads are classified into three deterministic priority tiers:

| Priority Tier | Permitted Operations | Concurrency & Resource Policy |
| :--- | :--- | :--- |
| **Highest** | Active audio/video decode, transport commands, volume adjustments, UI gestures. | Pre-empts lower priority tasks; dedicated engine thread; zero main-thread blocking. |
| **Medium** | Visible artwork decodes, active search query filtering, user-triggered library scan. | Bounded worker pool (`Dispatchers.IO`); throttled progress emissions. |
| **Lower** | Offscreen thumbnail generation, full library reconciliation, cache trimming. | Suspended during battery-saver mode; runs on idle dispatcher; yields to playback. |

---

## 3. Platform Background Execution Architecture

### 3.1 Android Lifecycle & Battery Compliance
- **`MediaSessionService` Foreground Mode**: Active audio playback runs inside a foreground service with a persistent media notification.
- **AudioTrack Direct Offload**: Media3 utilizes hardware-accelerated audio sinks where supported, allowing the application CPU to sleep between audio buffer refills.
- **Immediate Coroutine Cancellation**: In `Media3PlayerEngine`, position tracking (`positionJob`) is immediately cancelled when `onIsPlayingChanged(false)` triggers. No polling coroutines run while paused.
- **WorkManager for Deferred Work**: Non-essential scanning or cleanup is scheduled via Android `WorkManager` with battery-not-low constraints.

### 3.2 iOS & iPadOS Background Execution
- **`UIBackgroundModes: ["audio"]`**: Enforced strictly for continuous media playback.
- **Screen-Off Suspension**: Decorative Aurora Glass animations and lyrics scrolling are completely suspended when the display turns off or the app enters background.
- **Zero Unrestricted Run Loops**: No background timers or polling threads are executed while suspended.

### 3.3 Desktop Power Throttling (macOS, Windows, Linux)
- **Dynamic Polling Frequency**:
  - **Active Playback**: 250ms polling interval maintains smooth seekbar updates.
  - **Paused / Ready**: Polling interval automatically throttles to **500ms**, reducing background CPU wakeups by 50%.
  - **Completed / Idle**: Throttled to **1,000ms** or fully suspended.
- **Window Occlusion / Minimization**: When windows are minimized, Compose frame animation drops to 0 FPS, preventing GPU rendering churn while audio continues playing in the background.

---

## 4. Structured Coroutine Concurrency

1. **Explicit Scope Ownership**: ViewModels and Controllers own structured `CoroutineScope` instances tied directly to component lifecycles.
2. **Deterministic Teardown**: Calling `close()` on `PlayerEngine` or disposing UI screens cancels active coroutine scopes immediately (`scope.cancel()`).
3. **No Unbounded Fanout**: All batch operations (scanning, metadata extraction, thumbnailing) utilize chunked collections and bounded concurrency channels, preventing thread pool exhaustion.
