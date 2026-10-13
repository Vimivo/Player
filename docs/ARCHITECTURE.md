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

Conceptual `data`, `database`, and feature boundaries remain packages until team size or build performance justifies extra Gradle modules. Android persistence uses Room behind shared repository contracts; no Room or Android type crosses into `commonMain`.

## Local library architecture

`AndroidMediaStoreRepository` coordinates permission-aware MediaStore scanning, persisted SAF grants, the Room index, and debounced content observation. `MediaStoreScanner` uses volume version/generation tokens for incremental work; `SafMediaScanner` handles only user-selected documents and trees. Both scan off the main thread, are cancellable, and write bounded batches.

Room is the source for paged UI queries and categories. Its schema keeps stable URI-backed media references, metadata, favorites, history/resume positions, playlists, scan state, exclusions, and SAF grants. See [STORAGE.md](STORAGE.md) for the access and reconciliation policy.

## State and errors

View models expose immutable `StateFlow` values. Repositories expose streams and return `Result` for recoverable operations. `PlayerEngine` exposes a state stream, typed playback failures, engine capabilities, track selection, controls, and deterministic cleanup.

## Playback architecture

`PlayerEngine` is the platform-neutral control/state boundary. `Media3PlayerEngine` and `LibVlcPlayerEngine` translate native callbacks into the same immutable `PlaybackState`. Android player types and video-output views remain in `androidApp`.

`PlaybackEngineManager` owns exactly one engine, queue context, desired play state, and the set of engines attempted for the current open operation. Selection order is Media3 then LibVLC. A switch always closes the previous engine before constructing the next one and carries the queue and last known position forward.

Automatic fallback is intentionally narrow: only unsupported-container, decoder-initialization, and decoding failures can move from Media3 to LibVLC. Missing files, permission failures, HTTP authorization/status failures, and general network failures do not fall back. Each engine can be attempted only once per open operation, preventing fallback loops.

The current activity-hosted engines are a development foundation. A later foreground `MediaLibraryService` will own `PlaybackEngineManager`, MediaSession, notifications, and durable background playback without changing common contracts.

## Platform growth

Desktop and iOS targets can provide their own persistence, library scanners, and engines while retaining the shared contracts. No Android type is allowed in `commonMain`; URI values are opaque strings at the boundary.

