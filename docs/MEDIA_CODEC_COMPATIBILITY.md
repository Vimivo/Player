# Vimivo — Universal Media Codec & Container Compatibility Matrix

> [!NOTE]
> Authoritative compatibility specification verified under **Milestone 45 (Real Media Compatibility)** and **Milestone 46 (Advanced Playback Performance)**.
> All capabilities are categorized by explicit verification levels: `SUPPORTED_AND_VERIFIED`, `SUPPORTED_BUT_NOT_RUNTIME_VERIFIED`, `SOFTWARE_ONLY_VERIFIED`, `HARDWARE_ACCELERATED_VERIFIED`, `FALLBACK_VERIFIED`, and `UNSUPPORTED`.

---

## 1. Capability Status Taxonomy

Vimivo rejects universal binary boolean "supported/unsupported" claims. Media capability is classified along 10 distinct runtime dimensions:

1. **Container Demuxing**: Demuxer capability to parse container atoms, headers, indexes, and streams.
2. **Codec Bitstream Parsing**: Ability to identify and feed packetized bitstreams to decoders.
3. **Profile & Level Coverage**: Whether high profiles (e.g. HEVC Main 10, H.264 High 4:2:2) or high levels are supported.
4. **Hardware Decoder Availability**: Native OS GPU decoder ASIC (Apple VideoToolbox, Android MediaCodec, Windows D3D11VA, Linux VA-API/NVDEC).
5. **Software Decoder Availability**: CPU-based decoding fallback (FFmpeg libavcodec in libmpv / Media3 bundled decoders).
6. **Video Presentation & Color**: Correct color matrix conversion (BT.601, BT.709, BT.2020) and tone mapping.
7. **Audio Output Rendering**: Platform audio sink (CoreAudio, AudioTrack / AAudio, WASAPI, PulseAudio / PipeWire).
8. **Seeking Responsiveness**: Fast keyframe seeking vs sample-exact seeking.
9. **Multi-Track Switching**: Seamless runtime switching between multiple audio streams and subtitle tracks.
10. **HDR Presentation**: Native PQ / HLG rendering or high-quality SDR tone mapping without tint distortion.

---

## 2. Audio Format & Codec Compatibility

| Format / Codec | Container | Sample Formats | Channels | Sample Rates | Android (Media3) | iOS (AVFoundation) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | Status & Verification |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **MP3** (MPEG Audio Layer 3) | MP3, ID3v2 | 16/24-bit int | 1.0, 2.0 | 8 - 48 kHz | Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **AAC-LC** (Advanced Audio Codec) | M4A, MP4 | 16-bit int | 1.0 - 5.1 | 8 - 96 kHz | Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **AAC-HE / AAC-ELD** | M4A, MP4 | 16-bit int | 1.0 - 5.1 | 16 - 48 kHz | Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **FLAC** (Free Lossless Audio Codec) | FLAC, OGG | 16/24/32-bit | 1.0 - 7.1 | 8 - 192 kHz | Native | Native (iOS 11+) | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **ALAC** (Apple Lossless) | M4A, CAF | 16/24-bit int | 1.0 - 5.1 | 44.1 - 192 kHz | Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **WAV / LPCM** | RIFF WAV | 8/16/24/32-bit int, 32-bit float | 1.0 - 7.1 | 8 - 192 kHz | Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **Ogg Vorbis** | OGG | 16/24-bit float | 1.0 - 5.1 | 8 - 96 kHz | Native | Unsupported* | Native | Native | Native | `SUPPORTED_AND_VERIFIED` (Desktop/Android) |
| **Opus** | OGG, WebM | 16-bit float | 1.0 - 7.1 | 48 kHz (resampled) | Native | Partial (CAF) | Native | Native | Native | `SUPPORTED_AND_VERIFIED` |
| **AIFF / AIFC** | AIFF | 16/24/32-bit int | 1.0, 2.0 | 8 - 192 kHz | Unsupported* | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` (Desktop/iOS) |
| **WMA Standard / Pro** | ASF / WMA | 16/24-bit int | 1.0, 2.0 | 8 - 48 kHz | Fallback | Unsupported | Native | Native | Native | `SUPPORTED_AND_VERIFIED` (Desktop) |
| **AC-3 (Dolby Digital)** | MP4, MKV, TS | 16-bit int | 1.0 - 5.1 | 32, 44.1, 48 kHz | Passthrough/Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` (Where licensed) |
| **E-AC-3 (Dolby Digital Plus)** | MP4, MKV | 16-bit int | 1.0 - 7.1 | 32 - 48 kHz | Passthrough/Native | Native | Native | Native | Native | `SUPPORTED_AND_VERIFIED` (Where licensed) |
| **DTS / DTS-HD** | MKV, TS | 16/24-bit int | 1.0 - 7.1 | 44.1, 48 kHz | Passthrough | Unsupported | Native (SW) | Native (SW) | Native (SW) | `SOFTWARE_ONLY_VERIFIED` (Desktop) |

*\*Note: Platform-specific limitations are handled via fallback or clear diagnostic error messaging without application crash.*

---

## 3. Video Format & Codec Compatibility

| Container | Video Codec | Profile / Level | Bit Depth | Hardware Acceleration | Software Fallback | Android (Media3) | iOS (AVFoundation) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | Status |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **MP4 / MOV** | **H.264 (AVC)** | Constrained Baseline, Main, High (up to L5.2) | 8-bit | Yes | Yes | Native HW | Native HW | VideoToolbox | D3D11VA | VA-API / NVDEC | `HARDWARE_ACCELERATED_VERIFIED` |
| **MP4 / MOV** | **H.264 (AVC)** | High 10 / High 4:2:2 | 10-bit | Device-dependent | Yes | HW / SW Fallback | SW Fallback | VideoToolbox / SW | SW Fallback | VA-API / SW | `FALLBACK_VERIFIED` |
| **MP4 / MKV** | **H.265 (HEVC)** | Main Profile | 8-bit | Yes | Yes | Native HW | Native HW | VideoToolbox | D3D11VA | VA-API / NVDEC | `HARDWARE_ACCELERATED_VERIFIED` |
| **MP4 / MKV** | **H.265 (HEVC)** | Main 10 Profile (HDR10, HLG) | 10-bit | Yes (Modern GPU) | Yes | Native HW (API 24+) | Native HW (A10+) | VideoToolbox (M-series / Intel 7th+) | D3D11VA (Pascal+ / Navi+) | VA-API / NVDEC | `HARDWARE_ACCELERATED_VERIFIED` |
| **WebM / MKV** | **VP8** | Profile 0, 1 | 8-bit | Limited | Yes | Native HW/SW | Unsupported* | libavcodec | libavcodec | VA-API / SW | `SOFTWARE_ONLY_VERIFIED` |
| **WebM / MKV** | **VP9** | Profile 0 (SDR) | 8-bit | Yes | Yes | Native HW | Unsupported* | VideoToolbox / SW | D3D11VA | VA-API / NVDEC | `HARDWARE_ACCELERATED_VERIFIED` |
| **WebM / MKV** | **VP9** | Profile 2 (HDR10) | 10-bit | Yes (Modern GPU) | Yes | Native HW (API 28+) | Unsupported* | SW / VT | D3D11VA | VA-API / NVDEC | `SUPPORTED_AND_VERIFIED` |
| **MP4 / MKV** | **AV1** (AOMedia) | Main Profile (8-bit, 10-bit) | 8/10-bit | SoC-dependent | Yes (dav1d) | HW (Tensor / D9200+) / SW | HW (M3/A17+) / SW | VideoToolbox (M3+) / dav1d | D3D11VA (RTX 30+ / RX 6000+) / dav1d | VA-API / dav1d | `SUPPORTED_AND_VERIFIED` |
| **MOV / MKV** | **Apple ProRes** | 422 Proxy/LT/Standard/HQ, 4444 | 10/12-bit | Apple Silicon HW | Yes | SW Fallback | Native HW | VideoToolbox HW | libavcodec SW | libavcodec SW | `SUPPORTED_AND_VERIFIED` |
| **AVI / MKV** | **MPEG-4 Part 2** | Simple / Advanced Simple (XviD, DivX) | 8-bit | Legacy / Rare | Yes | SW Fallback | Unsupported | libavcodec SW | libavcodec SW | libavcodec SW | `SOFTWARE_ONLY_VERIFIED` |
| **TS / MPG** | **MPEG-2 Video** | Main Profile @ Main/High Level | 8-bit | GPU-dependent | Yes | SW Fallback | Unsupported | libavcodec SW / HW | D3D11VA / SW | VA-API / SW | `SOFTWARE_ONLY_VERIFIED` |

---

## 4. HDR & Color Space Capabilities

| Transfer Function | Color Matrix | High Dynamic Range | Bit Depth | Rendering Strategy | Tone Mapping Behavior |
| :--- | :--- | :---: | :---: | :--- | :--- |
| **SDR (BT.709)** | BT.709 / BT.601 | No | 8-bit | Standard direct GPU presentation | Passthrough (No tone mapping needed) |
| **SMPTE ST 2084 (PQ)** | BT.2020 | Yes (HDR10) | 10-bit | HDR display passthrough when supported | Dynamic Mobius/Reinhard tone mapping to SDR on standard displays |
| **ARIB STD-B67 (HLG)** | BT.2020 | Yes (Hybrid Log-Gamma) | 10-bit | Direct HLG display passthrough | SDR fallback curve calculation preserving highlight detail |
| **Dolby Vision (Profile 5, 8.1)** | BT.2020 / DCI-P3 | Yes | 10-bit | Display-dependent | Cross-compatibility tone mapping to HDR10 or SDR |

---

## 5. Subtitle Format & Styling Compatibility

| Subtitle Format | File Extension | Container Support | Desktop (libmpv + libass) | Android (Media3) | iOS (AVFoundation) | Fidelity & Features |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **SubRip (SRT)** | `.srt` | External sidecar, embedded MKV/MP4 | Native | Native | Native | Text styling, colors, timing offset (±5000ms) |
| **WebVTT** | `.vtt` | External sidecar, HLS/DASH | Native | Native | Native | Web-standard cues, CSS styling |
| **Advanced SubStation Alpha (ASS)** | `.ass`, `.ssa` | External sidecar, embedded MKV | Native (`libass`) | Partial (Text only) | Partial (Text only) | Vector shapes, karaoke, custom fonts, positioning |
| **Embedded MOV Text** | N/A | MP4, MOV (`tx3g`) | Native | Native | Native | Platform text rendering |
| **DVD Subpicture (VobSub)** | `.sub` + `.idx` | External, embedded MKV | Native | Unsupported | Unsupported | Bitmap graphic subtitles |
| **Blu-ray PGS** | `.sup` | Embedded MKV | Native | Unsupported | Unsupported | High-definition bitmap subtitles |

---

## 6. Probing & Error Recovery Summary

Vimivo uses a dual-engine probing strategy:
1. **Lightweight In-Memory Sniffing**: Inspects magic bytes (FLAC `fLaC`, Ogg `OggS`, RIFF/WAV `RIFF....WAVE`, Matroska/WebM `\x1A\x45\xDF\xA3`, QuickTime/MP4 `ftyp`) for instant (<1ms) container verification without spawning external processes.
2. **Detailed Demuxer Probing**: Safely reads container streams, audio sample rates, video dimensions, frame rates, color matrices, transfer functions, and chapters.
3. **Structured Error Codes**: Distinguishes `UNSUPPORTED_CODEC`, `UNSUPPORTED_PROFILE`, and `HARDWARE_ACCELERATION_FAILED` from ordinary network or filesystem I/O errors.
