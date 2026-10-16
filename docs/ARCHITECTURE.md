# Vimivo — Core Architecture Specification

This document details the multiplatform architecture, source-set boundaries, dependency directions, state management, media pipelines, and persistence across all supported targets.

---

## 1. High-Level Architecture Overview

Vimivo follows Clean Architecture and unidirectional data flow (MVI / reactive state holders) with platform adapters:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PRESENTATION LAYER                               │
│   Compose Multiplatform (Desktop)  │  Jetpack Compose (Android)  │ SwiftUI (iOS)│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Observes StateFlow<T> / Emits Intent
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION / DOMAIN LAYER                        │
│                   shared/src/commonMain (Pure Kotlin)                       │
│  • Domain Models (MediaItem, Playlist, SmartPlaylist, RecommendationItem)   │
│  • Repository Contracts (LibraryRepository, PersonalMediaRepository)        │
│  • Playback Contracts (PlayerEngine, UnifiedPlaybackQueue, PlaybackState)   │
│  • Business Engines (SmartRuleEvaluator, InsightsCalculator, ChangesetPlan) │
└───────────────────────┬───────────────────────────────┬─────────────────────┘
                        │ Implementations               │ Native Adapters
                        ▼                               ▼
┌───────────────────────────────────────┐   ┌─────────────────────────────────┐
│           DATA / PERSISTENCE          │   │         NATIVE ENGINES          │
│ • Desktop SQLite Schema v6 (JDBC/WAL) │   │ • macOS/Win/Linux: libmpv (JNA) │
│ • Android Room & SAF / MediaStore     │   │ • Android: Media3 / ExoPlayer   │
│ • iOS Native SQLite (WAL) & Keychain  │   │ • iOS: AVFoundation (AVPlayer)  │
└───────────────────────────────────────┘   └─────────────────────────────────┘
```

---

## 2. Module Boundaries & Dependency Rules

1. **`shared` Module**:
   - `src/commonMain`: Contains zero platform classes. No `java.io.*`, `java.sql.*`, `android.*`, `UIKit.*`, or `AVFoundation.*`. All media URIs are modeled as portable strings.
   - `src/desktopMain`: Common JVM desktop adapters (e.g., standard file paths, JDBC database bridges).
   - `src/androidMain`: Android-specific shared utilities without exposing Android UI elements.
2. **`desktopApp` Module**:
   - Owns Compose Multiplatform desktop presentation for macOS, Windows, and Linux.
   - Interacts with domain contracts through `DesktopLibraryController` and `DesktopLibraryRepository`.
   - Manages libmpv JNA lifecycle and platform-specific video render bridges (`MpvCocoaRenderer.mm`, `MpvWin32Renderer`, `MpvX11Renderer`).
3. **`androidApp` Module**:
   - Owns Android entry point (`MainActivity`), foreground playback service (`VimivoMediaService`), AndroidX Media3 integration, and Room database persistence.
4. **`iosApp` Module**:
   - Native Xcode SwiftUI project integrating the compiled `VimivoShared.framework`.
   - Connects to native `AVPlayer` and iOS `SecurityScopedMediaManager`.

---

## 3. Playback Queue Architecture

The application adheres to the **single authoritative playback queue** rule:
- **`UnifiedPlaybackQueue`**: Coordinates the active playing item, pending track order, shuffle permutations, and repeat modes (`OFF`, `ALL`, `ONE`).
- **Media Identity Isolation**: Uses `MediaId` and unique queue instance tokens so identical tracks in the same playlist or queue can be tracked and manipulated independently without collisions.
- **Continuous Auto-Advance**: Engine completion events trigger atomic advancement to the next queue item.
- **Persistence Across Sessions**: Saved to SQLite table `playback_queue_items` with ordered indices. Restored queues remain safely paused on application launch until an explicit user play command.

---

## 4. Metadata Architecture: Two Distinct Modes

Vimivo strictly separates metadata modifications into two explicit modes:

```text
                                  User Metadata Action
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
         MODE A: OVERRIDE                                MODE B: EMBEDDED
  ┌───────────────────────────────┐               ┌───────────────────────────────┐
  │  Stores in SQLite Database    │               │  Writes to Audio File Tags    │
  │  user_metadata_overrides      │               │  via jaudiotagger             │
  │                               │               │                               │
  │  • Original file untouched    │               │  • Supported: MP3, FLAC, OGG, │
  │  • Safe for read-only media   │               │    M4A, WAV                   │
  │  • Effective in search & UI   │               │  • Preflight check + backup   │
  │  • Immediate rollback/clear   │               │  • Post-write verification    │
  └───────────────────────────────┘               └───────────────────────────────┘
```

---

## 5. Persistence Architecture (SQLite Schema v6)

Desktop persistence is managed by `DesktopDatabase` with SQLite foreign-key enforcement and atomic transaction blocks:
- **`library_roots`**: Monitored folders with availability state.
- **`media_items`**: Authoritative indexed files with URI, title, format, duration, and timestamps.
- **`media_metadata`**: Extracted tags, codecs, dimensions, bitrate, sample rates.
- **`user_metadata_overrides`**: User-defined overrides for title, artist, album, genre, year, track/disc.
- **`user_artwork_overrides`**: User-selected custom artwork overrides.
- **`playlists` & `playlist_items`**: Custom collections with ordered positions.
- **`smart_playlists`**: Serialized AST rules for dynamic rule-based collections.
- **`media_hashes`**: SHA-256 content hashes for non-destructive duplicate detection.
- **`batch_operation_journal`**: Audit log of multi-step batch operations with stage tracking.
- **`workflow_rules`**: User automation rules and dry-run definitions.
- **`dismissed_recommendations`**: Tracked dismissed recommendations to prevent nagging.
- **`favorites`, `playback_history`, `playback_positions`, `media_bookmarks`**: Personal media data.

---

## 6. Security & Privacy Guarantees

- **No Remote Telemetry**: Zero analytics SDKs, zero crash-reporting servers, zero background phone-home pings.
- **Diagnostic Redaction**: All log messages and diagnostic bundle exports automatically mask user usernames, home paths, and credentials with `[REDACTED]`.
- **System Credential Stores**: Network stream passwords and keys are stored strictly in OS-managed secure storage (Windows Credential Manager, macOS Keychain, Linux Secret Service, Android Keystore, iOS Keychain).
