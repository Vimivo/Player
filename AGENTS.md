# Vimivo engineering rules

These rules apply to the entire repository.

## Product boundaries

- Vimivo is a free, ad-free, login-free, privacy-first music and video player.
- Never add advertising, analytics, trackers, or account infrastructure.
- Local media and explicitly authorized network sources are in scope. Do not bypass access controls or DRM.
- Vimivo ships five first-class apps: Android (minSdk 26), iOS (iPhone, iOS 16+), macOS, Windows, and Linux. A feature that applies to a platform family must ship on every platform in it, or the gap and its closest native alternative must be documented.

## Architecture

- Preserve the dependency direction: UI -> domain contracts <- platform/data implementations.
- Put media, playlist, settings, and playback contracts and business logic in `shared/commonMain`.
- Keep Android APIs (MediaStore, permissions, services, notifications, DataStore, Media3, and the libmpv JNI bridge) in `androidApp`.
- Keep macOS/JVM desktop APIs, JNA, libmpv, file dialogs, and packaging in `desktopApp`; common code must remain platform-neutral.
- iOS keeps a native SwiftUI UI in `iosApp`; all non-UI logic (indexing, persistence and migrations, playlists, queue, resume, preferences, search, stats) belongs in `shared` and is consumed through the `VimivoShared` framework. Keep AVFoundation, UIKit, and other Apple APIs in `iosApp` or `iosMain`.
- Xcode links `VimivoShared` from `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`; never point the iOS app at a fixed simulator or debug framework path.
- Select desktop platform adapters at runtime or through Gradle packaging configuration; never execute macOS native integrations on Windows or Windows native integrations on macOS.
- Do not expose Android `Uri`, `Context`, Media3, or libmpv types through shared interfaces.
- Use immutable UI state exposed as `StateFlow`; keep mutable flows private.
- Playback engines implement `PlayerEngine`, report capabilities and failures explicitly, and release native resources in `close()`.
- Desktop libmpv integration must use the documented client API, never shell commands or filename interpolation, and must report a missing native library as an actionable error.
- Persistence on every platform uses a non-destructive, versioned SQLite migration path under the per-user application data directory (Application Support on Apple platforms); never store databases in the bundle, repository, or temporary directory. Test-only databases may be in memory.
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
- When desktop code/build files change, also run `./gradlew :desktopApp:desktopTest :desktopApp:compileKotlinDesktop` and the desktop smoke test (`./scripts/verify_all.sh` runs it). Packaging for a given OS requires that OS (or its CI workflow) and must be reported separately.
- When iOS or shared code changes, run `xcodebuild test` for `iosApp` on a simulator (macOS hosts; `verify_all.sh` does this) or report that it must be verified in `ios.yml`.
- Never claim a task or build succeeded unless the command ran successfully.
- Add tests for shared business logic and regressions. Instrumentation tests require an emulator/device and must be reported separately if not run.
- Update architecture/decision documentation when boundaries, toolchains, permissions, or persistence change.
- The public application ID is permanent once published. Until then it may be set with `-Pvimivo.applicationId=...`.

