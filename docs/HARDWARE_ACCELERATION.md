# Vimivo — Hardware Acceleration Engineering & Policy

> [!NOTE]
> Authoritative hardware acceleration documentation for **Milestones 45 & 46**.
> Detail on hardware decoders, surface presentation, runtime capability verification, and recovery across Android, iOS, macOS, Windows, and Linux.

---

## 1. Hardware Acceleration Philosophy

Vimivo establishes a strict rule: **Never fake hardware acceleration**.
- A video is not "hardware accelerated" merely because the UI is drawn by a GPU.
- Hardware decoding requires an active dedicated hardware ASIC (e.g. Apple VideoToolbox engine, Qualcomm Adreno/Hexagon video engine, Intel QuickSync, NVIDIA NVDEC, AMD VCN) decoding compressed bitstreams into GPU surfaces without roundtripping through CPU memory.
- If hardware decoding is unavailable or fails, Vimivo reports this honestly via diagnostics and falls back gracefully to SIMD-optimized software decoding.

---

## 2. Platform Acceleration Matrix

| Platform | Native Decoder API | Typical GPU Implementations | Zero-Copy Support | HDR10 / HLG Passthrough | Supported Codecs |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **macOS** | **Apple VideoToolbox** | Apple Silicon M1-M4, Intel Iris/UHD | Yes (via CVPixelBuffer) | Yes (EDR / Apple XDR) | H.264, HEVC (8/10-bit), VP9, ProRes, AV1 (M3+) |
| **iOS / iPadOS** | **Apple VideoToolbox** | Apple A10-A18, M-series | Yes (via Metal/Layer) | Yes (EDR / Display P3) | H.264, HEVC (8/10-bit), ProRes, AV1 (A17 Pro+) |
| **Windows** | **D3D11VA / DXVA2** | NVIDIA GeForce, AMD Radeon, Intel Arc/UHD | Yes (Direct3D 11 surface) | Yes (Windows HDR API) | H.264, HEVC, VP9, AV1 (Modern GPU), MPEG-2 |
| **Linux** | **VA-API / NVDEC** | Intel (`iHD`), AMD (`radeonsi`), NVIDIA NVDEC | Yes (EGL/Vulkan interop) | Compositor-dependent | H.264, HEVC, VP9, AV1 (GPU-dependent) |
| **Android** | **MediaCodec** | Qualcomm Snapdragon, Samsung Exynos, MediaTek | Yes (Surface-backed) | Yes (MediaCodec HDR APIs) | H.264, HEVC, VP9, AV1 (Dimensity/Tensor) |

---

## 3. Hardware Acceleration Modes

Users and engines can select between three clear modes:

```kotlin
enum class HardwareAccelerationMode {
    AUTO,               // Default: Use best verified hardware decoder, fallback on error
    SOFTWARE,           // Safe Mode: Force CPU software decoding (libavcodec)
    HARDWARE_PREFERRED  // Aggressive: Require hardware decoding; fail or alert if unavailable
}
```

### Runtime Mode Configuration
- **libmpv (Desktop)**:
  - `AUTO`: `hwdec=auto-safe` (enables safe hardware acceleration profiles like `videotoolbox`, `d3d11va`, or `vaapi`).
  - `SOFTWARE`: `hwdec=no` (bypasses all hardware decoders).
  - `HARDWARE_PREFERRED`: `hwdec=auto` or platform explicit (`videotoolbox`, `d3d11va-copy`, `vaapi`).
- **Android Media3**:
  - `AUTO`: `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` with `setEnableDecoderFallback(true)`.
  - `SOFTWARE`: Exclusively selects software decoders (`c2.android.*`).

---

## 4. Runtime Capability Detection vs Probing

Vimivo differentiates between:
1. **Reported Capabilities**: What the OS claims it supports via `MediaCodecList` or libmpv options.
2. **Observed Capabilities**: What is actively decoding the stream.

Vimivo extracts observed capability metrics directly from the live engine:
- `hwdec-current`: The decoder currently active (e.g., `videotoolbox`, `d3d11va`, `vaapi`, or `no`).
- `hwdec-active`: Boolean confirmation that hardware decoding is actively outputting frames.
- `current-vo`: The active video output renderer (e.g. `libmpv`, `gpu-next`, `mediacodec-surface`).

If `hwdec-current` reports `no` while `AUTO` was requested, Vimivo's diagnostics panel informs the user that software decoding is active due to unsupported profile or decoder initialization failure.

---

## 5. Hardware Decoder Recovery

### Driver Loss & Device Reset
On desktop operating systems, connecting/disconnecting monitors or GPU power state changes can trigger a graphics context reset:
- **Windows**: `DXGI_ERROR_DEVICE_RESET` or `DXGI_ERROR_DEVICE_REMOVED`.
- **macOS / Linux**: Display reconfiguration events.

Vimivo's player engines handle this by:
1. Catching render context errors without crashing the process.
2. Re-creating the video surface and re-binding the native player context.
3. Restoring the exact playback timestamp and audio clock sync.
