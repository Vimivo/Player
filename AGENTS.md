# Vimivo engineering rules

These rules apply to the entire repository.

## Product boundaries

- Vimivo is a free, ad-free, login-free, privacy-first music and video player.
- Never add advertising, analytics, trackers, or account infrastructure.
- Local media and explicitly authorized network sources are in scope. Do not bypass access controls or DRM.
- Android is the only application target today. Keep common code portable, but do not create desktop or iOS apps until requested.

## Architecture

- Preserve the dependency direction: UI -> domain contracts <- platform/data implementations.
- Put media, playlist, settings, and playback contracts and business logic in `shared/commonMain`.
- Keep Android APIs (MediaStore, permissions, services, notifications, DataStore, Media3, and LibVLC) in `androidApp`.
- Do not expose Android `Uri`, `Context`, Media3, or LibVLC types through shared interfaces.
- Use immutable UI state exposed as `StateFlow`; keep mutable flows private.
- Playback engines implement `PlayerEngine`, report capabilities and failures explicitly, and release native resources in `close()`.
- Prefer a small number of cohesive Gradle modules. Introduce another module only with a measured build/ownership reason.

## Kotlin and dependencies

- Use production-quality Kotlin, explicit error handling, structured concurrency, and lifecycle-aware scopes.
- Avoid deprecated APIs, global mutable state, `GlobalScope`, blocking the main thread, and swallowed exceptions.
- Prefer official, maintained libraries. Verify versions and API signatures from primary sources before upgrades.
- Centralize versions and coordinates in `gradle/libs.versions.toml`.
- Keep platform APIs out of `commonMain`. Do not add `expect`/`actual` unless a real cross-platform use case requires it.
- Avoid abstractions that have only speculative value.

## UI and privacy

- Use Compose, Material 3 tokens, edge-to-edge layouts, accessible semantics, and adaptive behavior.
- Preserve light, dark, and AMOLED theme support. Do not copy another player's branding or layout.
- Ask for media permissions in context, explain denial, and continue gracefully with reduced functionality.
- Never upload library metadata, listening history, or media without an explicit user action.

## Quality gate

- Before completing a code task, run the narrowest relevant tests, `./gradlew detekt`, and `./gradlew :androidApp:assembleDebug` when Android code/build files changed.
- Never claim a task or build succeeded unless the command ran successfully.
- Add tests for shared business logic and regressions. Instrumentation tests require an emulator/device and must be reported separately if not run.
- Update architecture/decision documentation when boundaries, toolchains, permissions, or persistence change.
- The public application ID is permanent once published. Until then it may be set with `-Pvimivo.applicationId=...`.

