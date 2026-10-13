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

Conceptual `data`, `database`, and feature boundaries remain packages until team size or build performance justifies extra Gradle modules. Playlist persistence will be introduced behind `PlaylistRepository`; SQLite/Room is not selected prematurely.

## State and errors

View models expose immutable `StateFlow` values. Repositories expose streams and return `Result` for recoverable operations. `PlayerEngine` exposes a state stream, typed playback failures, engine capabilities, track selection, controls, and deterministic cleanup.

## Playback direction

Future Android implementations will adapt Media3 and LibVLC to `PlayerEngine`. Engine selection must use media requirements and `EngineCapabilities`, not UI knowledge. A foreground `MediaLibraryService` will own the active engine and notification/session lifecycle. This milestone intentionally provides neither fake playback nor incomplete service behavior.

## Platform growth

Desktop and iOS targets can provide their own persistence, library scanners, and engines while retaining the shared contracts. No Android type is allowed in `commonMain`; URI values are opaque strings at the boundary.

