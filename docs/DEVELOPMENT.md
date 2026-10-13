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

Configuration cache and parallel execution are enabled. CI should cache Gradle data but never commit `.gradle`, build outputs, signing material, or `local.properties`.

## Contributions

Keep shared models free of platform classes. Add an interface only at a real boundary and implement Android adapters under `platform/android`. New UI state must be immutable and observable through `StateFlow`. Test shared behavior without Android; use instrumentation tests only where the platform is the subject.

Release signing is intentionally not committed. Production builds must inject a protected keystore configuration in CI before publication.

