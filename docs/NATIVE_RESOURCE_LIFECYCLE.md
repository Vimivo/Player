# Vimivo — Native Media Decoder & GPU Resource Lifecycle

> [!NOTE]
> Authoritative engineering documentation detailing native resource ownership, decoder lifecycles, GPU surface detachment, and memory safety across **Milestones 63 and 64**.

---

## 1. Executive Summary

Vimivo manages low-level native playback libraries (libmpv via JNA on Desktop, Android Media3/MediaCodec NDK on Android, Apple AVFoundation on iOS/macOS) with strict deterministic ownership models. All native memory allocations, graphics contexts, and hardware decoder sessions are bound to explicit lifecycle scopes to guarantee zero handle leaks, zero double-frees, and zero orphaned GPU textures.

---

## 2. Desktop libmpv Native Ownership Model

### 2.1 The `nativeLock` Synchronization Boundary
On Desktop targets, every invocation of the libmpv C client API is synchronized under an immutable lock:
```kotlin
private val nativeLock = Any()
```
This guarantees that background state polling threads, UI seek dispatches, and engine termination (`close()`) can never execute concurrently against the same native `mpv_handle*` pointer.

### 2.2 Reusable Native Buffer Allocation
- **Issue Discovered**: In previous versions, JNA property queries (`doubleProperty`, `flagProperty`, `longProperty`) allocated a new native `com.sun.jna.Memory` buffer per call. Across 9 property checks at 4Hz polling, this resulted in **36 native C allocations per second** (129,600 allocations/hour).
- **Remediation**: Replaced per-tick allocations with a pre-allocated 8-byte `nativeBuffer = Memory(8L)`. Synchronized within `nativeLock`, this buffer is reused across all property reads, reducing JNA native heap allocation churn during playback polling to **exactly 0**.

### 2.3 Uninitialized Seek Guarding
Calling `seek` before a media file finishes demuxing returned native error code `-11` (`MPV_ERROR_COMMAND`). In `MpvPlayerEngine.kt`:
```kotlin
val isSeekable = flagProperty("seekable") ?: false
if (!isSeekable) {
    setProperty("start", positionSeconds.toString())
} else {
    command(listOf("seek", positionSeconds.toString(), if (exact) "absolute+exact" else "absolute"))
}
```
If demuxing is in progress, the seek position is safely buffered into the native `start` property, ensuring exact positioning without crashes or error logs.

---

## 3. GPU Surface & Decoder Lifecycle

| Lifecycle Event | Android (Media3) Action | Desktop (libmpv) Action | GPU Resource Impact |
| :--- | :--- | :--- | :--- |
| **Open Video** | Attaches `SurfaceView`; configures MediaCodec decoder. | Configures `vo=libmpv` or platform canvas (`wid`). | Hardware decoder allocated; texture pipeline bound. |
| **Switch Video ➔ Audio** | Releases `SurfaceView` buffer; switches to AudioTrack. | Detaches video surface (`vo=null`); frees video decoder. | **Zero GPU texture retention**; video decoder freed. |
| **Pause Playback** | Retains current frame on surface; halts decoder clock. | Pauses native video output clock (`pause=yes`). | Frame static; 0 rendering draw calls. |
| **Stop / Close** | Releases MediaCodec decoders and `SurfaceView` handle. | `mpv_terminate_destroy(context)`; disposes AWT Canvas. | **100% native context and VRAM released**. |

---

## 4. Hardware Decoder Fallback Matrix

Vimivo prioritizes native hardware acceleration while maintaining deterministic software decoding fallback:
- **macOS**: `videotoolbox` hardware decoding with fallback to software FFmpeg (`no`).
- **Android**: `MediaCodec` hardware decoders with automatic software codec fallback.
- **Windows**: `d3d11va` hardware decoding with fallback to software.
- **Linux**: `vaapi` hardware decoding with fallback to software.
