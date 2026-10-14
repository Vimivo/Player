# Development

## Requirements

- JDK 17
- Android SDK Platform 36 and a recent Android SDK build-tools installation
- Android Studio compatible with AGP 8.13

Set the SDK via `ANDROID_HOME` or an uncommitted `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

## Commands

```shell
./gradlew :androidApp:assembleDebug
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest
./gradlew detekt
./gradlew :androidApp:connectedDebugAndroidTest  # requires a device/emulator
```

Room schemas are exported to `androidApp/schemas` and must be committed with schema changes. Storage instrumentation tests use an in-memory Room database. Test MediaStore and SAF behavior on API 26, 32, 33, 34+, and at least one removable-storage device/provider; use generated or legally redistributable media only.

Playback instrumentation must additionally cover a real notification channel, lock screen, Bluetooth AVRCP controls, wired-headset unplug, phone-call focus loss, route changes, task removal, and process recreation on physical devices. `PlaybackServiceTest` validates the MediaController/session queue contract; these physical behaviors cannot be fully represented by host JVM tests.

Music UI instrumentation covers primary navigation and the exposed music categories. Repository/query unit tests cover every supported sort column and category binding. The service instrumentation suite covers queue restoration, repeat, shuffle, and saved position; run it on an API 26+ emulator or device because host tests cannot instantiate the Android media session.

Configuration cache and parallel execution are enabled. CI should cache Gradle data but never commit `.gradle`, build outputs, signing material, or `local.properties`.

## Contributions

Keep shared models free of platform classes. Add an interface only at a real boundary and implement Android adapters under `platform/android`. New UI state must be immutable and observable through `StateFlow`. Test shared behavior without Android; use instrumentation tests only where the platform is the subject.

Release signing is intentionally not committed. Production builds must inject a protected keystore configuration in CI before publication.

# macOS desktop

The desktop target requires JDK 17 and a current `libmpv` dynamic library. Homebrew's JDK is suitable for compilation and tests, but Compose Desktop blocks native packaging with that JDK because of known packaging issues. Use Amazon Corretto 17 for `:desktopApp:packageDmg`; do not disable the vendor safety check in committed configuration.

```shell
./gradlew :desktopApp:desktopTest :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDmg
```

The runtime searches the normal macOS dynamic-library paths for `libmpv`. Development builds can provide an explicit path with `-Dvimivo.mpv.library=/absolute/path/to/libmpv.dylib`. The native library is not currently bundled, so redistribution licensing and hardened-runtime signing must be resolved before a public desktop release.

