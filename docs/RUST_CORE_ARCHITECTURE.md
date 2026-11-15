# Vimivo Rust Core & Multiplatform Native Architecture

This document describes the unified Rust core architecture for Vimivo, bridging high-performance, memory-safe media logic to platform-native user interfaces across **iOS**, **macOS**, **Android**, **Windows**, and **Linux**.

---

## 1. System Overview

```
                    ┌─────────────────────────────────────────────────────┐
                    │                   PLATFORM UIs                      │
                    ├──────────────────┬─────────────────┬────────────────┤
                    │   iOS & macOS    │     Android     │ Windows & Linux│
                    │   (Swift /       │   (Kotlin /     │   (Rust UI /   │
                    │    SwiftUI)      │ Jetpack Compose)│     Slint)     │
                    └─────────┬────────┴────────┬────────┴────────┬───────┘
                              │                 │                 │
                              │ (UniFFI/Swift)  │ (UniFFI/JNI)    │ (In-Process Call)
                              ▼                 ▼                 ▼
                    ┌─────────────────────────────────────────────────────┐
                    │            crates/vimivo-ffi (UniFFI)               │
                    └──────────────────────────┬──────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      crates/vimivo-core (Pure Rust Crate)                       │
├───────────────────┬───────────────────┬───────────────────┬─────────────────────┤
│  Database & State │ PlayQueue & Logic │ Audio DSP & Tags  │ Parsers & Utilities │
│  • SQLite v6      │  • Navigation     │  • 10-Band EQ     │  • TitleParser      │
│  • Migrations 1-6 │  • Shuffle/Repeat │  • ReplayGain     │  • LrcLyricsParser  │
│  • Media Items    │  • Insert/Remove  │  • Clipping Guard │  • PlaylistM3u      │
│  • Playlists/Favs │  • Index Bounds   │  • RBJ Biquad     │  • Natural Order    │
└───────────────────┴───────────────────┴───────────────────┴─────────────────────┘
```

---

## 2. Workspace Organization

The project contains a standard Cargo workspace at the root:

| Crate | Purpose | Dependencies | Target Consumer |
|---|---|---|---|
| `crates/vimivo-core` | Core business logic, SQLite v6 persistence, playback state machine, audio DSP (10-band EQ, ReplayGain), offline title & LRC lyrics parsers. | `rusqlite` (bundled), `regex`, `rand`, `serde`, `thiserror` | `vimivo-ffi`, `vimivo-desktop` |
| `crates/vimivo-ffi` | UniFFI binding layer generating typed Swift and Kotlin bindings from Rust structs and methods. | `vimivo-core`, `uniffi`, `thiserror` | `iosApp` (Swift), `androidApp` (Kotlin) |
| `crates/vimivo-desktop` | 100% Rust native GUI application for Windows and Linux built with Slint. | `vimivo-core`, `slint` | Windows & Linux executables |

---

## 3. Core Modules in `vimivo-core`

### 3.1 Database & Migrations (`src/db.rs`)
- Embeds full SQLite engine (zero external C library dependencies).
- Authoritative forward migrations (Versions 1 through 6):
  - v1: `media_items`, `playlists`, `playlist_items`, `favorites`, `playback_history`, `bookmarks`, `settings`
  - v2: `lyrics` storage
  - v3: `playback_positions` resume checkpoints
  - v4: `user_metadata_overrides` non-destructive tag overrides
  - v5: Performance indexes (`idx_media_artist`, `idx_media_album`, `idx_media_folder`)
  - v6: `smart_playlists`

### 3.2 Playback Queue (`src/queue.rs`)
- Pure-logic queue state machine (`PlayQueue`).
- Seamless support for:
  - Shuffle-behind-current (preserves previous track history while randomizing upcoming tracks).
  - Repeat modes: `Off`, `All`, `One`.
  - Next, previous, jump to index, and jump to media ID.
  - Dynamic queue modification: `insert_next`, `append`, `remove`.

### 3.3 Audio DSP (`src/audio.rs`)
- **10-Band Graphic Equalizer**:
  - Exact standard centres: `31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz`.
  - Robert Bristow-Johnson (RBJ) audio EQ cookbook peaking biquad filters per band per channel.
  - In-place float PCM processing with `is_flat` zero-overhead bypass.
- **ReplayGain**:
  - Parses Track and Album gain (dB) and peak tags.
  - Automatic peak-clipping safety calculations.

### 3.4 Media Parsers (`src/parser.rs`)
- **TitleParser**: 100% offline regex-based title cleaner. Strips release-group noise (`1080p, BluRay, x264, HDR, AAC`) and parses `SxxEyy` or release years.
- **LrcLyricsParser**: Synchronized LRC parser supporting millisecond tags `[mm:ss.xx]`, offset adjustments, and binary-search active line lookup.
- **PlaylistFormats (M3U, PLS, XSPF)**:
  - M3U / M3U8 unicode-safe parser and exporter.
  - PLS (Winamp/Shoutcast) numbered key parser.
  - XSPF XML parser with entity-expansion/DTD injection defenses.

### 3.5 Video Tools & Cropping (`src/video.rs`)
- **BlackBarDetector**: 8-bit luma plane scanner detecting letterbox / pillarbox bars with noise-fraction thresholds.
- **VideoCropMath**: Centered ratio calculation, chroma-even alignment, and content-bounds cropping.
- **IntroSkip**: Chapter title heuristics (`intro, opening, recap, prologue, cold open`) or short first chapter detection with 85s fallback jump.

### 3.6 Universal Streaming & Reconnection (`src/streaming.rs`)
- **StreamValidator**: URL validation (length limits, forbidden control characters), protocol allowlist (`http`, `https`, `rtsp`, `rtsps`, `rtp`, `udp`, `mms`, `smb`, `sftp`, `ftp`, `nfs`, and capture devices). Rejects non-stream web pages (YouTube, TikTok, Instagram, Reddit).
- **StreamReconnectionPolicy**: Exponential backoff with bounded retries.

### 3.7 Smart Playlists & Template Renaming (`src/smart_playlist.rs`, `src/rename.rs`)
- **SmartPlaylistEvaluator**: AST condition evaluator matching media items across Title, Artist, Album, Genre, Duration, Favorite, and Year.
- **RenameTemplate**: Template-based file renaming (`{artist}`, `{album}`, `{title}`, `{track}`, `{year}`) with Windows/Linux forbidden character sanitization.

### 3.8 Playback Engine & libmpv Mapping (`src/engine.rs`)
- State machine contracts: `PlaybackStatus`, `PlaybackState`, and `PlayerCommand`.
- `MpvCommandMapper`: Direct property and command string formatting for `libmpv` C API integration.

---

## 4. Platform UI Bindings & Build Pipelines

### 4.1 iOS & macOS (Swift / SwiftUI)
- Built and packaged via `scripts/build_rust_apple.sh`:
  - Compiles for iOS Simulator (`aarch64-apple-ios-sim`), iOS Device (`aarch64-apple-ios`), and macOS (`aarch64-apple-darwin`).
  - Bundles headers and creates universal `iosApp/Frameworks/VimivoCoreRustFFI.xcframework`.
- Exported as a Swift Package in `iosApp/VimivoCoreRust/` with `Package.swift`:
  ```swift
  // swift-tools-version:5.9
  import PackageDescription

  let package = Package(
      name: "VimivoCoreRust",
      platforms: [.iOS(.v16), .macOS(.v12)],
      products: [.library(name: "VimivoCoreRust", targets: ["VimivoCoreRust"])],
      targets: [
          .binaryTarget(name: "VimivoCoreRustFFI", path: "../Frameworks/VimivoCoreRustFFI.xcframework"),
          .target(name: "VimivoCoreRust", dependencies: ["VimivoCoreRustFFI"], path: ".", sources: ["vimivo_ffi.swift"], publicHeadersPath: ".")
      ]
  )
  ```
- In Swift / SwiftUI:
  ```swift
  import VimivoCoreRust

  let db = try FfiDatabase.open(path: databasePath)
  let items = try db.queryItems(searchText: "Solaris")
  let queue = FfiPlayQueue()
  queue.replace(ids: items.map { $0.id }, startIndex: 0)
  ```

### 4.2 Android (Kotlin / Jetpack Compose)
- Built via `scripts/build_rust_android.sh` using `cargo-ndk`:
  - Compiles `libvimivo_ffi.so` for target ABIs (`arm64-v8a` and `x86_64`).
  - Automatically outputs to `androidApp/src/main/jniLibs/{arm64-v8a,x86_64}/libvimivo_ffi.so`.
- Kotlin bindings generated into `androidApp/src/main/java/dev/vimivo/core/rust/uniffi/vimivo_ffi/vimivo_ffi.kt`.
- Uses JNA (`net.java.dev.jna:jna`) configured in `androidApp/build.gradle.kts`.
- Verified packaging with `./gradlew :androidApp:assembleDebug` producing 100% self-contained APK.

### 4.3 Windows & Linux (Rust + Slint)
- Pure Rust execution:
  ```bash
  cargo run -p vimivo-desktop
  ```
- Slint compiles declarative `ui/appwindow.slint` into high-performance native machine code utilizing OpenGL, DirectX (on Windows), or Wayland/X11 (on Linux).
