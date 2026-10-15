# iOS & iPadOS Architecture

## Overview

Vimivo on iOS and iPadOS combines a native SwiftUI mobile interface with Kotlin Multiplatform shared contracts (`VimivoShared.framework`), delivering a privacy-first, offline-first media player that adheres strictly to Apple platform conventions and sandbox security.

## Architecture Boundaries

```
┌────────────────────────────────────────────────────────┐
│                   SwiftUI UI Layer                     │
│  MainTabView (iPhone) / NavigationSplitView (iPad)     │
│  MusicView | VideoView | PlaylistView | SettingsView   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                Authoritative Controllers               │
│  AVPlayerPlaybackEngine    │  AudioSessionController   │
│  NowPlayingManager         │  RemoteCommandController  │
│  SecurityScopedMediaManager│  IOSDatabase              │
│  KeychainCredentialStore   │                           │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│      Kotlin Multiplatform Core (VimivoShared)          │
│  Domain Contracts, Playlist/Queue State Logic, Models  │
└────────────────────────────────────────────────────────┘
```

## Native Multimedia Playback

- **AVPlayer Engine**: `AVPlayerPlaybackEngine` manages the authoritative playback lifecycle, time observers (`addPeriodicTimeObserver`), queue transitions, playback rate, and completion notifications (`AVPlayerItemDidPlayToEndTime`).
- **AVAudioSession**: Managed by `AudioSessionController` with category `.playback` and mode `.default`.
  - Handles system audio interruptions (`AVAudioSession.interruptionNotification`) such as incoming phone calls and Siri, pausing playback and resuming only when explicitly intended.
  - Handles route changes (`AVAudioSession.routeChangeNotification`) such as headphone disconnection (old device unavailable), automatically pausing playback to prevent unintended public audio.
- **Background Playback**: Declared via `UIBackgroundModes: ["audio"]` in `Info.plist`, allowing continuous background audio when the application is locked or placed in the background.

## System Media Controls

- **MPNowPlayingInfoCenter**: Managed via `NowPlayingManager`, exposing track metadata (title, artist, album, duration, elapsed time, playback rate) and artwork to the iOS Lock Screen, Dynamic Island, and Control Center.
- **MPRemoteCommandCenter**: Managed via `RemoteCommandController`, handling remote hardware media controls, Bluetooth headset actions, and system control center events:
  - Play / Pause / Toggle Play-Pause
  - Next Track / Previous Track
  - Change Playback Position (seeking)

## Picture-in-Picture (PiP)

- Integrated via `AVKit` / `AVPlayerViewController` with `allowsPictureInPicturePlayback = true`.
- Supported seamlessly across compatible iPhone and iPad devices.

## Sandboxed File Access & Security-Scoped Bookmarks

- Implemented in `SecurityScopedMediaManager`.
- iOS apps are strictly sandboxed. Files chosen via document picker or Files app provide security-scoped URLs.
- Persistent access across app restarts is maintained using security-scoped bookmarks:
  ```swift
  url.startAccessingSecurityScopedResource()
  let bookmark = try url.bookmarkData(options: .withSecurityScope, includingResourceValuesForKeys: nil, relativeTo: nil)
  ```
- Stale bookmarks and revoked permissions are handled gracefully with non-destructive fallback.
- Metadata and artwork are asynchronously extracted using native `AVAsset` and `AVAssetImageGenerator` without blocking the main actor.

## Persistence

- Implemented in `IOSDatabase` using native SQLite3 (`libsqlite3.tbd`) in WAL mode (`PRAGMA journal_mode=WAL`).
- Database file is stored in `Application Support/dev.vimivo.ios/vimivo.sqlite3`.
- Schemas include:
  - `media_items`: imported local and network media references, metadata, durations, and security bookmarks.
  - `playlists` & `playlist_items`: user playlists with preserved ordering.
  - `favorites`: bookmarked favorite tracks.
  - `playback_history`: play counts, last played timestamps, and completion statuses.
  - `playback_queue`: persisted active queue state.
  - `bookmarks`: saved timestamp markers within media items.

## Secure Credential Storage

- Implemented in `KeychainCredentialStore` using Apple Keychain Services (`Security.framework`).
- Credentials (e.g. for network storage/streaming) are stored using `kSecClassGenericPassword` with `kSecAttrAccessibleAfterFirstUnlock`.
- Zero credentials stored in SQLite or UserDefaults; zero credentials exposed in logs or diagnostics.

## UI/UX & Adaptive Layouts

- **Adaptive Structure**:
  - iPhone: `TabView` with bottom navigation bar, floating mini-player (`NowPlayingBar`), and full modal player (`NowPlayingView`).
  - iPad: Two-column `NavigationSplitView` with native sidebar navigation, expanded library grid, and responsive player layout.
- **Color & Theming**: Respects Dark and Light mode automatically using dynamic system colors and brand accent `#00D26A`.
- **Accessibility**: Standard VoiceOver labels, accessibility traits, dynamic type scaling, and sufficient contrast across all controls.
