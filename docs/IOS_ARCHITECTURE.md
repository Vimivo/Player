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
│  ResumePolicy, PlaybackSpeeds (consumed today);        │
│  persistence, indexing, queue next (ADR-020/021)       │
└────────────────────────────────────────────────────────┘
```

`PlaybackPreferences.swift` is the bridge to shared rules: it maps the Swift `MediaKind` to the Kotlin enum and calls
`ResumePolicy` and `PlaybackSpeeds`, so iOS resumes and offers speeds exactly like Android and desktop. Xcode links
the framework from `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, built by the
`embedAndSignAppleFrameworkForXcode` script phase for whichever SDK and configuration is being built.

## Getting Media In

- **Files app / document picker**: security-scoped bookmarks (below).
- **On My iPhone › Vimivo**: `DocumentsLibraryImporter` indexes the app's Documents folder (also reachable from Finder
  over USB) on launch and whenever the app becomes active. Items get stable `documents:<relative path>` IDs, and their
  stored URIs are refreshed because the container path changes when the app is updated.
- **Open in Vimivo / share sheet**: `CFBundleDocumentTypes` declares `public.audio` and `public.movie` with the
  `Alternate` rank; `AppRouter` imports and plays the file.
- **Deep links**: `vimivo://now-playing` (widgets) and `vimivo://play?id=<media id>`. Debug builds also accept
  `-VimivoOpenURL <url>` as a launch argument so simulator and CI smoke tests can drive playback.

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
  - `playback_history`: play counts, last played timestamps, resume positions, and completion statuses. Starting
    playback counts a play without touching the saved position; the position is saved every 10 s, on pause, when the
    app resigns active or enters the background, and when switching items.
  - `queue_items` + `queue_state`: the persisted queue and current index, restored paused at launch.
  - `bookmarks`: saved positions within media items, managed from the video player's options sheet.
- This describes the original Swift store. The app now opens the shared `LocalLibraryStore` (SQLite, schema v3,
  migrated non-destructively on open, in Application Support): see `DATABASE_ARCHITECTURE.md` §1a and the status
  section at the end of this file.

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

## Shared logic vs Swift (status after the queue migration)

Now in `shared` and used by the iOS app: the library store, schema and migrations (`LocalLibraryStore`, v3), the queue's
order, position, shuffle, repeat, edits, skip-on-failure, gapless "what follows" and save/restore (`PlayQueue`), bookmark
naming and the duplicate rule (`Bookmarks`), resume rules (`ResumePolicy`), the DSP chain (equalizer, boost, ReplayGain),
crop maths and black-bar detection, A–B region handling, lyrics (parser, active line, sidecar choice, storage), the sleep
timer countdown (`SleepTimer`, wall clock), waveform peaks (accumulator, codec, cache key), the snapshot naming.

Still Swift-only (each is either platform API or logic that has not been moved yet):

| Area | What stays in Swift | Why / next step |
|---|---|---|
| Player wiring | `AVQueuePlayer`, items, time and end observers, the legible (subtitle) output, picture-in-picture, now-playing and remote commands, audio session and interruptions | AVFoundation/MediaPlayer/UIKit: stays in `iosApp` by rule |
| Gapless preparation | which following song may be queued early (song to song only, not when it should resume part-way) | policy is Swift; the shared `PlaybackQueueController` has a similar rule for the other platforms: unify when the libmpv fallback (ADR-005) lands |
| Checkpoints and history | when to save the position (on item change, backgrounding, ending) and the completed flag | calls the shared store; the cadence rule could move next to `ResumePolicy` |
| Library listing | filter chips (recent, favourites), text search, grouping into albums and artists, sorting by date added | pure functions over media items: should move to shared with a shared `MediaItem` (see below) |
| Media model | Swift `MediaItem`, `Playlist`, `Bookmark`, `PlaybackHistory` structs mapped to the shared `Stored*` records in `IOSDatabase` | one model in shared would remove the mapping |
| Indexing and import | folder/file import, security-scoped bookmarks, metadata and artwork reading with AVFoundation, file existence and moved-container repair | AVFoundation and file-access APIs; the decisions (what counts as media, duplicates) are not shared yet |
| Preferences | `UserDefaults` for playback preferences and audio-effect settings | shared `AppSettings` exists for the other platforms; iOS has no shared settings store yet |
| Playlist editing | add/remove/reorder inside `PlaylistView` | trivial list edits over the shared store; move with the media model |
| Sleep fade | the volume ramp (`AVPlayer.volume`) | needs the player; the curve (`SleepFade`) and timer are shared |
| Statistics | none computed on iOS yet | not built |

