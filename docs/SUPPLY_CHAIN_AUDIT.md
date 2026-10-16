# Vimivo — Software Supply Chain & Dependency Audit

**Milestone**: 50  
**Target Release**: `1.0.0-rc.1`  
**Git Commit SHA**: `3924266`  
**SBOM Format**: CycloneDX 1.5 (`docs/sbom.json`)  
**Audit Status**: **APPROVED — ALL DEPENDENCIES COMPLIANT**

---

## 1. Supply Chain Architecture & Controls

Vimivo enforces strict supply chain integrity:
1. **Centralized Version Catalog**: All JVM, Android, and Compose library versions are declared exclusively in `gradle/libs.versions.toml`. No ad-hoc string coordinates in build scripts.
2. **Official Maintained Sources**: All Maven dependencies originate from Maven Central or Google Maven repository (`mavenCentral()`, `google()`).
3. **No Dynamic or Changing Versions**: No `+` or dynamic version selectors.
4. **Least-Privilege CI Permissions**: GitHub Actions workflows run with explicit read-only token permissions (`contents: read`) except where artifact uploading requires elevated scope.
5. **Secrets Separation**: Build steps never require release signing secrets; signing occurs strictly in isolated release jobs guarded by repository environment protection rules.

---

## 2. CycloneDX Software Bill of Materials (SBOM)

The authoritative SBOM is published in `docs/sbom.json` according to CycloneDX 1.5 specification:

| Component Name | Version | Category | License | Purpose in Vimivo |
| :--- | :--- | :--- | :--- | :--- |
| `kotlin-stdlib` | 2.3.20 | Framework | Apache-2.0 | Language standard library |
| `compose-multiplatform` | 1.9.3 | Framework | Apache-2.0 | Declarative cross-platform UI |
| `kotlinx-coroutines-core` | 1.10.2 | Library | Apache-2.0 | Structured concurrency |
| `kotlinx-serialization-core` | 1.9.0 | Library | Apache-2.0 | JSON metadata serialization |
| `androidx-media3-exoplayer` | 1.11.1 | Library | Apache-2.0 | Android native playback engine |
| `androidx-media3-session` | 1.11.1 | Library | Apache-2.0 | Foreground playback service & lockscreen |
| `libvlc-all` | 3.7.7 | Library | LGPL-2.1-only | Android video playback fallback |
| `sqlite-jdbc` | 3.53.4.0 | Library | Apache-2.0 | Desktop SQLite database driver |
| `jaudiotagger` | 3.0.1 | Library | LGPL-2.1-or-later | Audio metadata ID3/Vorbis reader/writer |
| `jna` | 5.19.1 | Library | Apache-2.0 | Native C binding interface for libmpv |
| `coil-compose` | 3.4.0 | Library | Apache-2.0 | Asynchronous image loading for artwork |
| `libmpv` | 2.0.0 | Native Lib | LGPL-2.1-or-later | Desktop multimedia playback engine |
| `ffmpeg-avcodec` | 61.1.102 | Native Lib | LGPL-2.1-or-later | Multimedia demuxing and decoding |
| `libplacebo` | 360.0.0 | Native Lib | LGPL-2.1-or-later | Video rendering and color management |
| `libass` | 9.0.0 | Native Lib | ISC | Advanced SubStation Alpha subtitle renderer |
| `dav1d` | 7.0.0 | Native Lib | BSD-2-Clause | High-performance AV1 video decoder |

---

## 3. License Compatibility & Redistribution Audit

- **Application License**: Apache-2.0 (commercial friendly, permissive, patent-granting).
- **LGPL Compliance**:
  - `libmpv`, `ffmpeg`, and `jaudiotagger` are licensed under LGPL 2.1.
  - Desktop distribution dynamically links to `libmpv.2.dylib` / `libmpv-2.dll` / `libmpv.so.2` at runtime via JNA; no static linking into application binaries.
  - End users may replace the dynamic libraries with compatible custom versions without modifying Vimivo source code.
- **BSD / ISC / MIT Components**:
  - `dav1d` (BSD-2-Clause), `libass` (ISC), and standard MIT components include copyright notices in `docs/THIRD_PARTY_NOTICES.md`.
- **GPL Contamination Check**:
  - FFmpeg and libmpv are built with `--enable-gpl=no` or LGPL configuration, preserving LGPL redistribution rights for the pre-packaged application.
