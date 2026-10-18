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

## Video Player

The full-screen player (`VideoPlayerView`) mirrors the Android `AdvancedVideoPlayer` instead of using
`AVPlayerViewController`, so both platforms share one control layout and gesture vocabulary.

- **Rendering**: `PlayerLayerView` hosts an `AVPlayerLayer` for the engine's shared `AVPlayer`; aspect modes
  (Fit, Crop, Stretch, 16:9, 4:3) map to `videoGravity` plus a fixed-ratio frame, and pinch zoom scales the layer.
- **State**: `VideoPlayerModel` (`@MainActor`) owns transient UI state (controls, lock, HUDs, sheets, track
  lists). Playback stays in `AVPlayerPlaybackEngine`; the engine's `defaultRate` keeps the chosen speed across
  resume and queue changes.
- **Gestures**: `PlayerGestureSurface` uses UIKit recognisers: tap toggles controls, double tap seeks ±10 s on the
  outer 40% (accumulating while the window is open) or toggles play in the middle, hold plays at 2×, one-finger
  horizontal pan seeks with a HUD, vertical pan adjusts brightness (left, via `UIScreen.brightness`, restored on
  close) or system volume (right, via a hidden `MPVolumeView`, which also suppresses the system volume HUD), and
  pinch zooms 100–400%. Swipes that start within 24 pt of the top or bottom edge are left to the system.
- **Rotation lock**: `OrientationController` (consulted by `VimivoAppDelegate`) pins the current orientation via
  `requestGeometryUpdate` and releases it when the player closes.
- **Tracks and subtitles**: audio and subtitle choices come from the asset's `AVMediaSelectionGroup`s. Subtitle cues
  are rendered by SwiftUI from the engine's `AVPlayerItemLegibleOutput` (attached to each item before playback;
  re-attached after a subtitle switch because AVFoundation stops pushing cues to an output after a mid-playback
  selection change).
- The player closes itself when the queue finishes.

## Picture-in-Picture (PiP)

- `AVPictureInPictureController` is created from the player's `AVPlayerLayer` when
  `isPictureInPictureSupported()`; the top bar shows a PiP button only on supporting devices.

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
- Database file is stored in `Application Support/vimivo/vimivo.db`.
- Migrations are additive: `media_items` gains the nullable `width`, `height`, `size_bytes`, and `date_added_millis`
  columns only when `PRAGMA table_info` shows they are missing. Videos imported before these columns existed are
  backfilled from the file (display dimensions after the preferred transform, and file size) when the Videos screen opens.
- Video thumbnails are generated on device with `AVAssetImageGenerator` and cached as JPEG in
  `Caches/video-thumbnails/`; they are never uploaded.
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
  - iPhone: `TabView` with bottom navigation bar, floating mini-player (`NowPlayingBar`), and full modal player (`NowPlayingView`). Tapping the mini-player while a video is current reopens the full-screen video player.
  - Home, Music, and Videos follow the Android layout: a "Good to see you" home with Continue watching / Recently played / Recently added / Favorites / Quick playlists sections, single-select filter chips (`FilterChipRow`), and video rows with 16:9 thumbnails and a "duration · resolution · size" line.
  - iPad: Two-column `NavigationSplitView` with native sidebar navigation, expanded library grid, and responsive player layout.
- **Color & Theming**: Respects Dark and Light mode automatically using dynamic system colors and brand accent `#00D26A`.
- **Accessibility**: Standard VoiceOver labels, accessibility traits, dynamic type scaling, and sufficient contrast across all controls.
