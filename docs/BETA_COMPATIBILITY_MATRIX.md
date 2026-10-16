# Vimivo Real-World Media Compatibility Matrix

This document defines the verified codec, container, metadata, and hardware acceleration support across Vimivo's native multimedia playback backends.

---

## 1. Playback Engine Architecture by Platform

| Platform | Core Playback Engine | Hardware Acceleration Engine | Native Linkage Model |
| :--- | :--- | :--- | :--- |
| **Android** | Google Media3 ExoPlayer 1.5.1 / 1.11.1 | Android `MediaCodec` (Surface-backed) | Native NDK / Java bindings |
| **macOS Desktop** | libmpv 2.0.0 | macOS `VideoToolbox` (OpenGL / Cocoa) | JNA dynamic loader (`libmpv.2.dylib`) |
| **Windows Desktop** | libmpv 2.0.0 | Direct3D11 (`d3d11va`) / `dxva2` | JNA dynamic loader (`mpv-2.dll`) |
| **Linux Desktop** | libmpv 2.0.0 | `vaapi` / `vdpau` (X11 & Wayland) | JNA dynamic loader (`libmpv.so.2`) |
| **iOS / iPadOS** | Apple `AVFoundation` (`AVPlayer`) | Apple `VideoToolbox` (Metal-backed) | Swift / Kotlin Multiplatform interop |

---

## 2. Audio Format & Codec Compatibility Matrix

| Audio Format / Extension | Codec | Sampling Rates / Bit Depths | Android (Media3) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | iOS (AVPlayer) |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **MP3** (`.mp3`) | MPEG-1/2 Audio Layer III | 44.1kHz – 48kHz, up to 320kbps | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Full |
| **FLAC** (`.flac`) | Free Lossless Audio Codec | 16-bit / 24-bit, 44.1kHz – 192kHz | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Full |
| **M4A / AAC** (`.m4a`, `.aac`) | AAC-LC, HE-AAC v1/v2 | 44.1kHz – 96kHz, up to 320kbps | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Full |
| **ALAC** (`.m4a`) | Apple Lossless Audio Codec | 16-bit / 24-bit, up to 192kHz | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Full |
| **OGG / Vorbis** (`.ogg`) | Ogg Vorbis | Variable bitrates, up to 48kHz | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ⚠️ Limited |
| **Opus** (`.opus`, `.ogg`) | Opus Audio Codec | 48kHz, voice to high-bitrate | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ⚠️ iOS 17+ |
| **WAV** (`.wav`) | LPCM (Integer & Float) | 16-bit, 24-bit, 32-bit float | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Full |
| **AIFF** (`.aiff`, `.aif`) | Audio Interchange File Format | 16-bit, 24-bit PCM | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Full |

*Note on Apple Platforms*: AVFoundation requires iOS 17+ for native Opus container playback; Ogg Vorbis on iOS requires custom CoreAudio decoding or future LibVLC/mpv mobile integration.

---

## 3. Video Container & Video Codec Compatibility Matrix

| Container / Extension | Primary Video Codec | Common Audio Codec | Android (Media3) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | iOS (AVPlayer) |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **MP4** (`.mp4`, `.m4v`) | H.264 / AVC (Baseline to High) | AAC, MP3, AC3 | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec |
| **MP4** (`.mp4`) | H.265 / HEVC (8-bit / 10-bit) | AAC, EAC3 | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec |
| **MP4** (`.mp4`) | AV1 (`av01`) | AAC, Opus | ✅ (Android 10+) | ✅ HW/SW | ✅ HW/SW | ✅ HW/SW | ⚠️ iPhone 15 Pro+ |
| **MKV** (`.mkv`) | H.264, HEVC, VP9, AV1 | FLAC, AAC, AC3, DTS | ✅ Full | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec | ⚠️ Remux req. |
| **WebM** (`.webm`) | VP8, VP9, AV1 | Vorbis, Opus | ✅ Full | ✅ HW Dec | ✅ HW Dec | ✅ HW Dec | ⚠️ Limited |
| **MOV** (`.mov`) | Apple ProRes, H.264, HEVC | LPCM, AAC | ✅ H.264/HEVC | ✅ Full | ✅ Full | ✅ Full | ✅ HW Dec |
| **AVI** (`.avi`) | MPEG-4 Part 2, DivX, XviD | MP3, AC3 | ⚠️ SW Dec | ✅ Full | ✅ Full | ✅ Full | ❌ Unsupported |

---

## 4. Subtitle Formats & Rendering Matrix

| Subtitle Format | Implementation Mode | Text Styling & Positioning | Embedded MKV/MP4 | External Sidecar File (`.srt`, `.vtt`) |
| :--- | :--- | :--- | :---: | :---: |
| **SubRip** (`.srt`) | Plain UTF-8 text parser & Compose UI / mpv | Basic formatting (bold, italic, color) | ✅ Full | ✅ Full |
| **WebVTT** (`.vtt`) | W3C WebVTT standard parser | Cue styling & multi-line positioning | ✅ Full | ✅ Full |
| **ASS / SSA** (`.ass`, `.ssa`) | Advanced SubStation Alpha via libass | Full typography, karaoke effects, positions | ✅ Full (libmpv/desktop) | ✅ Full (libmpv/desktop) |
| **Bitmap VobSub** (`.idx` / `.sub`) | DVD/Blu-ray graphic overlays | Pixel-exact DVD/BD subtitle bitmaps | ✅ Desktop | ⚠️ Mobile limited |

---

## 5. Metadata & Tagging Compatibility

- **ID3 Standards**: ID3v1, ID3v2.2, ID3v2.3, ID3v2.4 supported.
- **Character Encodings**: Full UTF-8, UTF-16 with BOM, ISO-8859-1, and non-Latin character sets (Bengali `আমার গান`, Japanese `日本語`, Cyrillic, Arabic).
- **Cover Artwork**:
  - Embedded APIC (ID3) / METADATA_BLOCK_PICTURE (FLAC) / `covr` atom (MP4).
  - External directory images: `cover.jpg`, `folder.png`, `front.jpg` detected automatically.
- **Duration & Bitrate**: Real-time header parsing with zero playback interruption.
