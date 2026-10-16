# Vimivo Platform Compatibility & Support Policy

> [!NOTE]
> This policy documents minimum supported operating system versions, target CPU architectures, and platform-specific hardware capabilities.

---

## 1. Operating System Compatibility Matrix

| Platform | Minimum Supported Version | Recommended Version | Target CPU Architectures | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Android** | Android 8.0 (API 26) | Android 14 / 15 (API 34/35) | `arm64-v8a`, `armeabi-v7a`, `x86_64` | Primary Release Target |
| **macOS** | macOS 12.0 (Monterey) | macOS 14 (Sonoma) / 15 (Sequoia) | Apple Silicon (`arm64`), Intel (`x86_64`) | Fully Verified Desktop Target |
| **Windows** | Windows 10 (Build 19041+) | Windows 11 (22H2+) | `x86_64`, `aarch64` | Verified Desktop Target |
| **Linux** | Ubuntu 22.04 LTS / Debian 12 | Ubuntu 24.04 LTS / Fedora 40 | `x86_64`, `aarch64` | Verified Desktop Target |
| **iOS / iPadOS** | iOS 16.0 | iOS 17 / 18 | `arm64` | Architecture Ready (Awaiting Release) |

---

## 2. Audio & Video Codec Compatibility Standards

| Category | Fully Supported Formats | Hardware Acceleration |
| :--- | :--- | :--- |
| **Lossless Audio** | FLAC, ALAC, WAV, AIFF | Software decode (negligible CPU) |
| **Lossy Audio** | MP3, AAC, M4A, Ogg Vorbis, Opus | Hardware / Platform-optimized |
| **Standard Video** | MP4, MKV, MOV, WebM (H.264, VP9) | Hardware acceleration on all platforms |
| **Next-Gen Video** | H.265 / HEVC, AV1, 10-bit HDR | Hardware where GPU supported; software fallback |
| **Subtitles** | SRT, ASS/SSA (styled), VTT, Embedded | Vector rendering via libmpv / Media3 |

---

## 3. Deprecation & Support Lifecycles

- **OS Deprecations**: An operating system version is only deprecated when vendor security support terminates and modern toolchains no longer produce stable binaries.
- **Advance Notice**: Any planned increase in minimum API or OS level must be documented in release notes at least one minor release cycle ahead of time.
- **Hardware Preservation**: Vimivo avoids requiring proprietary hardware features or dropping older devices when software fallback decoders remain performant.
