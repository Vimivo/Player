# Vimivo — Kotlin Multiplatform & Compose Modernization Baseline

This document records the modernized toolchain, compiler versions, multiplatform dependencies, and runtime constraints for Vimivo Milestones 59 and 60.

---

## 1. Toolchain & Compiler Inventory

| Component | Target Version | Verification Status | Rationale & Compatibility Notes |
| :--- | :--- | :--- | :--- |
| **Kotlin Compiler** | `2.3.20` | COMPILED & TESTED | Latest stable Kotlin compiler with K2 architecture, cross-platform coroutine optimizations, and strict KMP source set separation. |
| **Compose Multiplatform** | `1.9.3` | COMPILED & TESTED | JetBrains Compose Multiplatform release with stable desktop Skiko rendering, unified Material 3 tokens, and accessible semantic nodes. |
| **Android Gradle Plugin (AGP)** | `8.13.2` | COMPILED & TESTED | Official stable AGP version supporting Android API 36 compile/target SDKs and Java 17 bytecode output. |
| **Gradle** | `8.13` | RUNTIME VERIFIED | Gradle wrapper pinned to 8.13 with configuration cache reuse enabled. |
| **JDK Target** | `JDK 17 LTS` | COMPILED & TESTED | Built and verified with OpenJDK / Eclipse Temurin 17; compatible with modern Android D8/R8 desugaring and desktop jlink packaging. |
| **Android SDK** | `minSdk=26`, `compileSdk=36`, `targetSdk=36` | ASSEMBLE VERIFIED | Complies with Google Play 2026 target API level requirements while preserving backward compatibility down to Android 8.0. |

---

## 2. Key Multiplatform Dependencies

All dependency coordinates are centralized in `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.3.20"
compose-multiplatform = "1.9.3"
agp = "8.13.2"
media3 = "1.11.1"
jna = "5.19.1"
sqlite-jdbc = "3.53.4.0"
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.8.1"
detekt = "1.23.8"
```

### 2.1 AndroidX Media3
- **Version**: `1.11.1`
- **Modules**: `media3-exoplayer`, `media3-session`, `media3-ui`, `media3-datasource`, `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-exoplayer-rtsp`.
- **Integrity**: Standardized on Media3 official components; legacy `com.google.android.exoplayer2` packages remain strictly excluded.

### 2.2 Desktop libmpv Integration
- **Bridge**: Java Native Access (JNA) `5.19.1`.
- **Packaging**: Direct C-API bindings to `mpv_create`, `mpv_initialize`, `mpv_command`, and `mpv_set_option_string`.
- **Security**: Never executes shell processes; all paths, URLs, and properties are marshaled using C pointer parameters to prevent shell injection.

### 2.3 SQLite Storage Engine
- **Desktop**: SQLite JDBC driver `3.53.4.0` running SQLite 3.45+ with Write-Ahead Logging (WAL) and strict foreign keys.
- **Android**: Framework SQLite (Android `android.database.sqlite`) via platform SQLite driver.

---

## 3. Verification & Quality Gates

Each toolchain modernization step is validated through our automated gate:
1. Static analysis via Detekt (`./gradlew detekt`) with 0 tolerance for cyclomatic complexity or code smells.
2. Shared multiplatform unit tests (`./gradlew :shared:desktopTest`).
3. Desktop application compilation (`./gradlew :desktopApp:compileKotlinDesktop`).
4. Desktop unit and integration test suite (`./gradlew :desktopApp:desktopTest`).
5. Android unit test suite (`./gradlew :androidApp:testDebugUnitTest`).
6. Android debug APK compilation and packaging (`./gradlew :androidApp:assembleDebug`).

All 6 stages execute cleanly and succeed deterministically.
