# Architecture decisions

## ADR-000A: One MediaLibraryService playback authority

**Decision:** Host one `MediaLibrarySession` in `VimivoPlaybackService`. Adapt the existing engine manager to Media3's `Player` contract instead of creating separate Media3 and LibVLC services.

**Reason:** One session prevents simultaneous playback and duplicate system entries while preserving fallback compatibility. Media3 supplies notification, lock-screen, media-button, foreground-service, and browsing behavior. UI code communicates only through a `MediaController`. Persisted queue state restores paused after process death.

## ADR-000: Room-backed Android media index

**Decision:** Use Room 2.8.5 behind common repository contracts. MediaStore remains authoritative for shared media; SAF is limited to user-selected URIs and trees. Store metadata and stable references, never full media copies.

**Reason:** Room provides schema validation, indexed paging, transactions, and Flow invalidation while leaving future platform adapters replaceable. Vimivo does not request all-files access; broad audio/video permissions remain optional because system pickers provide a no-permission path.

## ADR-001: Two Gradle modules initially

**Decision:** Use `androidApp` and `shared`, expressing finer boundaries as packages.

**Reason:** This preserves clean dependency direction and future portability without paying configuration and build costs for many near-empty modules. Split modules when ownership, isolation, or build measurements justify it.

## ADR-002: API 26 minimum, API 36 target

**Decision:** Android 8.0 is the minimum. Compile and target API 36.

**Reason:** API 26 supports the selected Media3 and LibVLC lines and offers modern notification/background primitives while retaining broad device coverage.

## ADR-003: Engine abstraction in common code

**Decision:** Media3 and LibVLC will be Android adapters behind `PlayerEngine`.

**Reason:** Playback policy and UI remain testable and portable. Capability reporting prevents assuming that both engines support identical behavior.

## ADR-004: DataStore now, database later

**Decision:** Persist preferences with AndroidX DataStore. Keep playlist/library persistence behind repository contracts and defer the database choice until its schema is defined.

**Reason:** DataStore is the official fit for small settings. Premature database integration would create migrations without a validated model.

## ADR-005: Toolchain compatibility baseline

**Decision:** Kotlin 2.3.20, Compose Multiplatform 1.9.3, AGP 8.13.2, Gradle 8.13, JDK 17, Koin 4.2.2, Media3 1.11.1, Coil 3.4.0, and stable LibVLC 3.7.7.

**Reason:** AGP 8.13.2 explicitly supports Kotlin 2.3 and API 36; Compose 1.9.3 and Coil 3.4.0 share a compatible baseline. LibVLC 4 remains EAP, so the latest stable 3.x release is used. Version upgrades require dependency resolution, tests, and an Android build.

## ADR-006: Configurable pre-release application ID

**Decision:** Default to `dev.vimivo.app`, overridable with `vimivo.applicationId` until publication.

**Reason:** The working product name may change. After first publication the chosen ID must never change because Android/store upgrade identity depends on it.

## ADR-007: Bounded Media3-to-LibVLC fallback

**Decision:** Prefer Media3. Fall back once to LibVLC only for unsupported-container, decoder-initialization, or decode failures.

**Reason:** Retrying missing, unauthorized, or unreachable sources in another engine cannot fix the source and can hide actionable errors. Tracking attempted engines eliminates loops. Switching closes the previous engine first, preventing concurrent playback.

## ADR-008: Native ABI and page-size policy

**Decision:** Package `arm64-v8a`, `armeabi-v7a`, and `x86_64`; exclude obsolete x86. Keep LibVLC 3.7.7 while it is the latest stable 3.x artifact.

**Reason:** The selected AAR officially publishes all four ABIs. ARM64 and x86_64 cover modern devices and emulator testing; ARMv7 remains supported by the application minimum. Inspection found 16 KB-aligned (`2**14`) ARM64 ELF load segments, and the generated APK passes `zipalign -P 16`. Runtime testing on a 16 KB emulator/device remains required.

## ADR-009: One music state path through the playback service

**Decision:** All music surfaces use the service-backed `PlaybackController` and `PlaybackViewModel`; collection screens resolve queue items through `MediaRepository` and never create an engine or player.

**Reason:** A single command and observation path keeps the mini player, Now Playing, notification, queue, and external media controls synchronized across recreation and backgrounding.

## ADR-010: Media schema version 2 records release year

**Decision:** Add nullable `releaseYear` to indexed media with a Room auto-migration from schema 1 to 2.

**Reason:** Album details can expose available local release metadata without inventing values. The nullable additive column preserves existing libraries safely, and the exported schema is committed for migration verification.

## ADR-011: Video surfaces remain session-owned

**Decision:** The full-screen video UI renders the existing service session player and uses a process-local diagnostics bridge only for engine-specific track/capability state. Media3 and LibVLC both attach through the session adapter; Compose never constructs an engine.

**Reason:** This preserves one playback lifecycle across fullscreen, PiP, backgrounding, configuration changes, and fallback while still allowing track selection and accurate decoder information.
