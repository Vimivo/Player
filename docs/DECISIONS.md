# Architecture decisions

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

**Decision:** Kotlin 2.3.20, Compose Multiplatform 1.9.3, AGP 8.13.2, Gradle 8.13, JDK 17, Koin 4.2.2, Media3 1.11.0, Coil 3.4.0, and stable LibVLC 3.7.7.

**Reason:** AGP 8.13.2 explicitly supports Kotlin 2.3 and API 36; Compose 1.9.3 and Coil 3.4.0 share a compatible baseline. LibVLC 4 remains EAP, so the latest stable 3.x release is used. Version upgrades require dependency resolution, tests, and an Android build.

## ADR-006: Configurable pre-release application ID

**Decision:** Default to `dev.vimivo.app`, overridable with `vimivo.applicationId` until publication.

**Reason:** The working product name may change. After first publication the chosen ID must never change because Android/store upgrade identity depends on it.
