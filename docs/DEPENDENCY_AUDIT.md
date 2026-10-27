# Vimivo Dependency & License Audit

Audit date: 2026-10-15.
Centralized dependency coordinates: `gradle/libs.versions.toml`.

---

## 1. Core Technology Stack & Versions

| Component | Version | License | Maintenance / Source |
|---|---|---|---|
| Kotlin Multiplatform | `2.1.10` | Apache 2.0 | JetBrains |
| Compose Multiplatform | `1.7.3` | Apache 2.0 | JetBrains |
| Kotlin Coroutines | `1.10.1` | Apache 2.0 | JetBrains |
| Kotlin Serialization | `1.8.0` | Apache 2.0 | JetBrains |
| AndroidX Media3 (ExoPlayer) | `1.5.1` | Apache 2.0 | Google / AndroidX |
| libmpv + FFmpeg (own LGPL build) | `0.41.0` / `9.0.2` | LGPL 2.1+ | mpv / FFmpeg projects |
| AndroidX Room | `2.7.0-alpha13` | Apache 2.0 | Google / AndroidX |
| JNA (Desktop Native Bridge) | `5.16.0` | LGPL 2.1 / Apache 2.0 | java-native-access |
| libmpv (Desktop Engine) | `0.39.0` (LGPL build) | LGPL 2.1+ | mpv project |
| Jaudiotagger (Desktop Audio) | `3.0.1` | LGPL 3.0 | ijabz |
| Detekt | `1.23.8` | Apache 2.0 | detekt |

---

## 2. Licensing Compliance

- **LGPL 2.1 / 3.0 (libmpv, FFmpeg, Jaudiotagger)**:
  - Dynamically linked via shared libraries (`.dylib`, `.so`, `.dll`) or runtime JARs; no proprietary modifications made to LGPL source code.
  - User can replace native libraries with compatible ABI builds.
  - Licenses and notices are cataloged in `docs/THIRD_PARTY_NOTICES.md` and `docs/LICENSE_AUDIT.md`.
- **Apache 2.0 (Kotlin, Compose, Media3, Coroutines)**:
  - Fully compliant; standard Apache 2.0 notices preserved.

---

## 3. Dependency Security Assessment

- All dependencies are sourced from official Maven Central, Google Maven, or JetBrains Maven repositories.
- Zero known high or critical CVE vulnerabilities in the active dependency set.
- Transitive dependencies audited via Gradle dependency verification.
