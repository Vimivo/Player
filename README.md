# Vimivo — Every Sound. Every Frame.

[![CI Shared](https://github.com/Vimivo/Player/actions/workflows/shared.yml/badge.svg)](https://github.com/Vimivo/Player/actions/workflows/shared.yml)
[![CI macOS](https://github.com/Vimivo/Player/actions/workflows/macos.yml/badge.svg)](https://github.com/Vimivo/Player/actions/workflows/macos.yml)
[![CI Android](https://github.com/Vimivo/Player/actions/workflows/android.yml/badge.svg)](https://github.com/Vimivo/Player/actions/workflows/android.yml)
[![CI iOS](https://github.com/Vimivo/Player/actions/workflows/ios.yml/badge.svg)](https://github.com/Vimivo/Player/actions/workflows/ios.yml)
[![CI Windows](https://github.com/Vimivo/Player/actions/workflows/windows.yml/badge.svg)](https://github.com/Vimivo/Player/actions/workflows/windows.yml)
[![CI Linux](https://github.com/Vimivo/Player/actions/workflows/linux.yml/badge.svg)](https://github.com/Vimivo/Player/actions/workflows/linux.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Vimivo is a free, privacy-first, ad-free, login-free, offline universal music and video player built with Kotlin Multiplatform and Compose Multiplatform for **Android**, **iOS / iPadOS**, **macOS**, **Windows**, and **Linux**.

---

## Product Principles

- **Offline-First & Privacy-First**: 100% local processing. Zero mandatory accounts, zero cloud telemetry, zero advertising, and zero tracking.
- **User-Owned Media**: Works directly with local audio and video files across internal storage, external SD cards, and authorized document providers. Original files are never modified without explicit user approval.
- **Native Platform Performance**: Powered by platform-native multimedia engines: Android Media3 / ExoPlayer, Apple AVFoundation, and native `libmpv` for desktop hardware-accelerated video rendering.
- **Safe Persistence**: Built on an authoritative SQLite schema (version 6) with non-destructive forward migrations, WAL concurrency, and integrity checks.

---

## Major Features

- **Audio Playback**: Gapless audio, 10-band graphic equalizer with presets and auto-attenuating preamp, native ReplayGain (Track & Album mode) with peak clipping safety, pitch preservation during speed changes (0.5x–2.0x), sleep timer, and synchronized LRC lyrics.
- **Video Playback**: Hardware-accelerated GPU rendering, aspect ratio overrides (Fit, Fill, 16:9, 4:3, 21:9), native frame screenshots, 90-degree step rotation, GPU video adjustments (brightness, contrast, saturation, gamma), external subtitle loading (`.srt`, `.vtt`, `.ass`, `.ssa`), real-time subtitle & audio delay synchronization (±5000ms), chapter stepping, and A-B repeat looping.
- **Unified Playback Queue**: Authoritative persistent queue with shuffle, repeat modes (Off, All, One), history tracking, automatic resume checkpoints, and playlist management.
- **Rule-Based Smart Playlists**: Dynamic collection evaluation using typed AST rules across genre, artist, year, duration, play count, and favorites.
- **Offline Recommendation Engine**: 100% offline explainable recommendations (Continue Watching, Continue Listening, Top Artists, Rediscover Favorites, Recently Added, Cold Start) with dismissal persistence.
- **Metadata Editing (Two Modes)**:
  - *Mode A (Database Overrides)*: Non-destructive metadata and artwork overrides stored in SQLite, leaving original media bytes 100% untouched.
  - *Mode B (Embedded Tag Writing)*: Writable audio tags and embedded artwork for supported audio containers (MP3, FLAC, OGG, M4A, WAV) with preflight checks, automatic backup, and post-write verification.
- **Batch File Renaming & Organization**: Previewable template-based renaming (`{artist}`, `{album}`, `{track}`, `{title}`, `{year}`) with Windows reserved name guards, collision detection, and database reference reconciliation.
- **Missing Media Reconciliation**: Detection of relocated media, candidate matching (filename, title, size), and atomic database relinking without losing playlist history.
- **Workflow Automation & Health Dashboard**: Non-destructive dry-run rules, persistent batch operation journal (`batch_operation_journal`), and real-time library health diagnostics.
- **Playlist Portability**: Standard M3U and M3U8 import/export with full Unicode and Bengali script preservation.

---

## Supported Platforms

| Platform | Minimum OS | Engine Backend | Packaging Format |
|---|---|---|---|
| **Android** | Android 8.0 (API 26+) | Media3 / ExoPlayer | APK, AAB |
| **iOS / iPadOS** | iOS 16.0+ | Native AVFoundation | Xcode Archive, IPA |
| **macOS** | macOS 12.0+ (x86_64, arm64) | libmpv (bundled runtime) | Standalone DMG, APP |
| **Windows** | Windows 10+ (x64) | libmpv (Win32 HWND) | EXE, MSI |
| **Linux** | GLIBC 2.31+ (Ubuntu 20.04+) | libmpv (X11 XID / MPRIS 2) | DEB, RPM, AppImage |

---

## Quick Start & Development

### Prerequisites
- **JDK 17** (Eclipse Temurin recommended)
- **Android SDK Platform 36** (with build-tools `36.0.0`)
- **Xcode 15+** (for macOS native bridge compilation and iOS simulator/device builds)

### One-Command Verification
Run the complete developer quality gate:

```shell
./scripts/verify_all.sh
```

Or via Gradle:

```shell
./gradlew verifyAll
```

### Building Android
```shell
# Run unit tests and static analysis
./gradlew :androidApp:testDebugUnitTest :androidApp:detekt

# Build debug APK
./gradlew :androidApp:assembleDebug

# Build release App Bundle
./gradlew :androidApp:bundleRelease
```

### Running macOS Desktop
```shell
# Run tests
./gradlew :desktopApp:desktopTest

# Launch Compose Desktop application
./gradlew :desktopApp:run

# Package standalone macOS DMG
./gradlew :desktopApp:packageDmg
```

---

## Project Structure

```
Vimivo/
├── shared/               # Platform-neutral domain contracts, models, repositories, and UI
│   ├── src/commonMain/   # Pure Kotlin domain logic, StateFlow stores, models
│   ├── src/desktopMain/  # Desktop JVM common implementations
│   └── src/androidMain/  # Android shared adapters
├── desktopApp/           # Compose Desktop application (macOS, Windows, Linux)
│   ├── src/desktopMain/  # libmpv bridge, SQLite v6 persistence, UI panels, desktop controls
│   └── src/desktopTest/  # 143+ comprehensive desktop & regression test suites
├── androidApp/           # Native Android application
│   ├── src/main/         # Media3 service, MediaSession, Jetpack Compose UI, Room DB
│   └── src/test/         # Android unit tests
├── iosApp/               # Native iOS / iPadOS SwiftUI application
│   └── VimivoApp/        # AVPlayer engine, Lock Screen controls, sandboxed Files integration
├── docs/                 # Complete architecture, user, and release documentation
└── scripts/              # Developer automation and validation scripts
```

---

## Documentation Index

- **Developer Guides**:
  - [Getting Started & Build Guide](docs/DEVELOPMENT.md)
  - [Android Development Guide](docs/development/ANDROID.md)
  - [iOS / iPadOS Development Guide](docs/development/IOS.md)
  - [macOS Development Guide](docs/development/MACOS.md)
  - [Windows Development Guide](docs/development/WINDOWS.md)
  - [Linux Development Guide](docs/development/LINUX.md)
- **Architecture & System References**:
  - [Core Architecture](docs/ARCHITECTURE.md)
  - [Playback Architecture Guide](docs/PLAYBACK_ARCHITECTURE.md)
  - [Database & Migration Guide](docs/DATABASE_ARCHITECTURE.md)
  - [Supported Media Format Matrix](docs/FORMAT_SUPPORT.md)
  - [Feature Matrix](docs/FEATURE_MATRIX.md)
- **User & Release Guides**:
  - [User Guide](docs/USER_GUIDE.md)
  - [Installation Guide](docs/INSTALLATION.md)
  - [Maintenance Handoff](docs/MAINTENANCE_HANDOFF.md)
  - [Hotfix & Incident Runbook](docs/HOTFIX_RUNBOOK.md)
  - [Release Quality Report](docs/RELEASE_QUALITY_REPORT.md)

---

## Contributing & Security

- Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for branch guidelines, code style rules, and PR requirements.
- For security disclosures, please consult [SECURITY.md](SECURITY.md).

---

## License

Vimivo is open-source software licensed under the [MIT License](LICENSE). Third-party dependencies and native media libraries retain their respective licenses (LGPL/GPL/Apache 2.0). See [Third-Party Notices](docs/THIRD_PARTY_NOTICES.md) and [License Audit](docs/LICENSE_AUDIT.md).
