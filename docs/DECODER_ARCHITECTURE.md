# Vimivo — Native Decoder Architecture & Fallback Strategy

> [!NOTE]
> Authoritative decoder architecture and fallback specification documented for **Milestone 45** and **Milestone 46**.
> Covers decoder lifecycle, platform-specific decoding engines, and single-decision bounded fallback mechanics across Android, iOS, macOS, Windows, and Linux.

---

## 1. Architectural Philosophy & Dependency Flow

Vimivo adheres to strict architectural boundaries:
```
UI Layer (Compose Multiplatform / Native Views)
         │
         ▼
Shared Domain Contracts (commonMain: PlayerEngine, MediaCompatibilityModels)
         │
         ▼
Platform Playback Adapters (androidApp / desktopApp / iosApp)
  ├── Android:  Media3 (ExoPlayer + MediaCodec + LibVLC fallback)
  ├── Apple:    AVFoundation (AVPlayer + VideoToolbox)
  └── Desktop:  libmpv (Client API + VideoToolbox / D3D11VA / VA-API + libavcodec)
```

No platform-specific types (e.g. Android `Uri`, Media3 `ExoPlayer`, Apple `AVPlayer`, libmpv native handles) leak into `commonMain`.

---

## 2. Shared Decoder Contracts

The shared module defines the hardware decoding strategy and real-time diagnostic model:

```kotlin
enum class HardwareAccelerationMode {
    AUTO,               // Best available hardware acceleration with safe fallback
    SOFTWARE,           // Force CPU software decoding (libavcodec / platform SW)
    HARDWARE_PREFERRED  // Prefer GPU acceleration; log failure if unavailable
}

interface PlayerEngine {
    // Hardware acceleration mode selection
    fun setHardwareAccelerationMode(mode: HardwareAccelerationMode) {}

    // Precise and low-latency seeking
    fun seekTo(positionMillis: Long, exact: Boolean = true)

    // Real-time diagnostics snapshot
    fun getDiagnosticsSnapshot(): PlaybackDiagnosticsSnapshot? = null
}
```

---

## 3. Platform Decoder Engines

### 3.1 Android (Media3 & MediaCodec)
- **Primary Engine**: `Media3PlayerEngine` built on ExoPlayer 1.5.1.
- **Decoder Factory**: `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` or `ON`.
- **MediaCodec Integration**: Uses `MediaCodecSelector.DEFAULT` with hardware-accelerated decoders prioritized (e.g. `c2.qti.avc.decoder`, `OMX.Exynos.AVC.Decoder`).
- **Decoder Fallback**: Configured via `setEnableDecoderFallback(true)` on `DefaultRenderersFactory`, allowing ExoPlayer to automatically step down from an incompatible hardware decoder to a secondary hardware or software decoder (e.g. Google OMX/c2 software decoders `c2.android.avc.decoder`).
- **Error Interception**: `FALLBACK_ERRORS` catches `ERROR_CODE_DECODER_INIT_FAILED`, `ERROR_CODE_DECODER_QUERY_FAILED`, `ERROR_CODE_DECODING_FAILED`, and `ERROR_CODE_DECODING_FORMAT_UNSUPPORTED` to trigger a controlled single-step engine or profile fallback.

### 3.2 macOS (libmpv & VideoToolbox)
- **Primary Engine**: `MpvPlayerEngine` with documented client API.
- **Hardware Decoder (`hwdec`)**:
  - `AUTO`: `mpv_set_option_string(handle, "hwdec", "auto-safe")` — dynamically chooses the safest hardware decoder (VideoToolbox via CVPixelBuffer).
  - `SOFTWARE`: `mpv_set_option_string(handle, "hwdec", "no")` — utilizes FFmpeg `libavcodec` SIMD-optimized software decoders.
  - `HARDWARE_PREFERRED`: `mpv_set_option_string(handle, "hwdec", "videotoolbox")` — forces Apple VideoToolbox hardware decoding.
- **Render Context**: Bridges video into Cocoa surfaces via JAWT OpenGL/Metal pipeline without blocking the main UI thread.

### 3.3 Windows (libmpv & D3D11VA)
- **Hardware Decoder (`hwdec`)**:
  - `AUTO`: `hwdec=auto-safe` (Direct3D 11 Video Acceleration `d3d11va`).
  - `SOFTWARE`: `hwdec=no`.
  - `HARDWARE_PREFERRED`: `hwdec=d3d11va-copy` or `d3d11va`.
- **Driver Loss Recovery**: Detects DXGI device reset / removal and safely recovers textures.

### 3.4 Linux (libmpv & VA-API / NVDEC)
- **Hardware Decoder (`hwdec`)**:
  - `AUTO`: `hwdec=auto-safe` (selects VA-API on Intel/AMD, NVDEC on NVIDIA).
  - `SOFTWARE`: `hwdec=no`.
  - `HARDWARE_PREFERRED`: `hwdec=vaapi` or `nvdec`.
- **Wayland / X11**: Automatically adapts to active compositor window surface.

---

## 4. Single-Decision Bounded Fallback Mechanics

A major risk in multimedia players is the "infinite fallback cycle" where a corrupt or unsupported file triggers repeated player recreation, thrashing memory and freezing the UI.

Vimivo enforces **Bounded Single-Decision Fallback**:
1. **Initial Playback Attempt**: Launched with user's preferred engine and hardware acceleration mode (`AUTO`).
2. **Error Interception**: If a native decode failure (`DECODER_INIT_FAILED`, `HARDWARE_ACCELERATION_FAILED`) occurs:
   - Error is classified into `PlaybackFailureReason`.
   - Current playback position (`playbackPositionMillis`) is recorded.
   - The failing engine is completely closed, releasing native resources and GPU textures.
3. **Single Fallback Execution**:
   - Secondary playback attempt is made in `SOFTWARE` mode or with fallback engine (e.g. LibVLC on Android).
   - If fallback succeeds: Playback resumes from the recorded position with a non-intrusive warning (`FALLBACK_VERIFIED`).
   - If fallback fails: The playback session immediately terminates with an actionable, precise diagnostic error code (`UNSUPPORTED_CODEC` or `UNSUPPORTED_PROFILE`).
4. **No Secondary Retries**: Under no circumstances does the player retry a third time for the same media item.

---

## 5. Native Resource Lifecycle & Safety

- **Native Handle Disposal**: `MpvPlayerEngine.close()` and `Media3PlayerEngine.close()` strictly release all native pointers and observers.
- **Thread Ownership**: libmpv render callbacks and property change notifications are processed on a dedicated background event thread, avoiding deadlocks with the Compose UI dispatchers.
- **Memory Footprint**: Native frame queues and demuxer readahead buffers are strictly bounded to prevent unbounded memory growth during long sessions.
