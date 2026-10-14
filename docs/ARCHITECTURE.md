# Architecture

Vimivo uses Clean Architecture principles with MVVM at presentation boundaries. The current two-module layout is intentionally smaller than the conceptual package map.

```text
androidApp                       shared
platform/android  ----------->  domain contracts
MediaStore, DataStore           core models
Media3, LibVLC (future)         settings contracts
permissions, service (future)   playback contracts
                                features + Compose UI
```

## Modules and packages

- `shared`: KMP module containing `core`, `domain`, `settings`, `playback`, `features`, and shared Compose UI. It currently declares only an Android target; adding native/desktop targets does not require moving common code.
- `androidApp`: the Android entry point and Android-specific adapters under `platform/android`. Media engine dependencies live here so their types cannot leak into common code.
- `desktopApp`: the macOS-first JVM desktop entry point. Compose Desktop owns presentation and native packaging; a JNA adapter owns the documented libmpv client lifecycle. The initial video output is libmpv-owned while a safe render API bridge is evaluated.

Conceptual `data`, `database`, and feature boundaries remain packages until team size or build performance justifies extra Gradle modules. Android persistence uses Room behind shared repository contracts; no Room or Android type crosses into `commonMain`.

## Local library architecture

`AndroidMediaStoreRepository` coordinates permission-aware MediaStore scanning, persisted SAF grants, the Room index, and debounced content observation. `MediaStoreScanner` uses volume version/generation tokens for incremental work; `SafMediaScanner` handles only user-selected documents and trees. Both scan off the main thread, are cancellable, and write bounded batches.

Room is the source for paged UI queries and categories. Its schema keeps stable URI-backed media references, metadata, favorites, history/resume positions, playlists, scan state, exclusions, and SAF grants. See [STORAGE.md](STORAGE.md) for the access and reconciliation policy.

Personal organization is local-only. `PlaylistRepository` owns ordered mixed audio/video playlists, while `OrganizationRepository` exposes bookmarks and playback history without Android types. Room uses indexed prefix search across media metadata and playlist names. Playlist items intentionally keep opaque IDs when media is temporarily unavailable; resolving a queue skips unavailable rows without deleting the user's playlist. M3U/M3U8 import and export use the Storage Access Framework and only resolve references already present in the authorized library.

Settings remain a portable `AppSettings` model behind `SettingsRepository`; Android persists them with Preferences DataStore. UI consumes the immutable settings flow, while platform adapters read only the preferences they implement. User-controlled JSON backup is handled by `LocalBackupManager` through SAF and uses stable media URIs to reconnect Room organization records without copying media or storage grants.

## State and errors

View models expose immutable `StateFlow` values. Repositories expose streams and return `Result` for recoverable operations. `PlayerEngine` exposes a state stream, typed playback failures, engine capabilities, track selection, controls, and deterministic cleanup.

## Playback architecture

`PlayerEngine` is the platform-neutral control/state boundary. `Media3PlayerEngine` and `LibVlcPlayerEngine` translate native callbacks into the same immutable `PlaybackState`. Android player types and video-output views remain in `androidApp`.

`PlaybackEngineManager` owns exactly one engine, queue context, desired play state, and the set of engines attempted for the current open operation. Selection order is Media3 then LibVLC. A switch always closes the previous engine before constructing the next one and carries the queue and last known position forward.

Authorized network media uses this same service-owned path. Media3 includes its HLS and DASH source modules and remains preferred; LibVLC is available only through the existing bounded compatibility fallback. `NetworkViewModel` observes the MediaController for buffering, live, seekability, and errors, and may retry transport failures at most three times. It never owns a player or sends a submitted URL anywhere except the selected origin server.

Automatic fallback is intentionally narrow: only unsupported-container, decoder-initialization, and decoding failures can move from Media3 to LibVLC. Missing files, permission failures, HTTP authorization/status failures, and general network failures do not fall back. Each engine can be attempted only once per open operation, preventing fallback loops.

`VimivoPlaybackService` is the sole `MediaLibraryService` and playback owner. It creates one service-scoped `PlaybackEngineManager` and exposes it through a `SessionPlayerAdapter`, so Media3 system controls work identically when Media3 or LibVLC is active. Activities and Compose code connect with `PlaybackController`; they never construct or retain a native engine.

Media3 owns the foreground notification, lock-screen state, Bluetooth/headset commands, and controller arbitration. The adapter owns the authoritative queue, repeat/shuffle state, engine transitions, completion advancement, and persistence. Restored queues remain paused until an explicit user or system play command.

## Music presentation architecture

Music screens query `MediaRepository` through `LibraryViewModel`; category selection and sorting are expressed as shared `LibraryQuery` values and translated to indexed Room queries. Album, artist, album-artist, genre, folder, recent, and favorite screens therefore reuse the same paged data path without loading an entire large library on the main thread.

`PlaybackController` is the only UI command path into the service session. The persistent mini player, full-screen Now Playing screen, collection actions, and queue editor all observe one `PlaybackViewModel` backed by that controller. UI code never constructs an engine or independently owns playback state. Artwork thumbnail reads are bounded, cached, and dispatched off the main thread.

## Video presentation architecture

`AdvancedVideoPlayer` renders the service session's `MediaController` surface and sends every transport command through `PlaybackViewModel`. A process-local `PlaybackDiagnostics` bridge exposes the active engine's immutable track and capability state and delegates track selection back to the service-owned manager; it does not own or create an engine. `SessionPlayerAdapter` attaches the same Android surface contract to Media3 and LibVLC and detaches it during teardown.

The activity owns Android-only window concerns: Picture-in-Picture entry, immersive system bars, and orientation policy. Gesture interpretation remains presentation state, while sensitivity is persisted through the shared settings contract. Per-item resume continues to use Room playback history and resets positions within the final ten seconds.

## Platform growth

Desktop and iOS targets can provide their own persistence, library scanners, and engines while retaining the shared contracts. The macOS target now supplies its first libmpv engine and file-selection UI. No Android or macOS type is allowed in `commonMain`; URI values are opaque strings at the boundary.

