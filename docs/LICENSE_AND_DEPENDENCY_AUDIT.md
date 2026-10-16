# Vimivo — Third-Party License & Dependency Compliance Audit

**Milestones**: 57 & 58  
**Target Release**: `1.0.0-rc.1`  
**Git HEAD**: `0d7ecba`  
**Date**: 2026-10-16  

---

## 1. Compliance Principles & Distribution Obligations

Vimivo is distributed as open-source, free software. Redistributed third-party dependencies are strictly reviewed to ensure 100% compliance with open-source licensing models (Apache 2.0, MIT, BSD, ISC, and LGPL v2.1+).

No proprietary, non-free, or GPL-incompatible components are bundled into release artifacts.

---

## 2. Dependency Audit Catalog

| Component | Upstream Project | Version | Primary License | Redistribution Terms & Compliance Status |
| :--- | :--- | :--- | :--- | :--- |
| **libmpv** | mpv-player/mpv | 2.0.0 | LGPL v2.1+ | Dynamic library linking; source code available upstream | **COMPLIANT** |
| **FFmpeg** | FFmpeg Project | 6.1.1 | LGPL v2.1+ | Built with `--disable-gpl --disable-nonfree`; dynamically linked | **COMPLIANT** |
| **libass** | libass | 0.17.1 | ISC | Permissive; copyright notice included in notices | **COMPLIANT** |
| **libplacebo** | haasn/libplacebo | 6.338.0 | LGPL v2.1+ | Dynamically linked inside libmpv | **COMPLIANT** |
| **Media3** | AndroidX Media3 | 1.11.1 | Apache 2.0 | Permissive; notice included in APK/AAB | **COMPLIANT** |
| **LibVLC Android** | VideoLAN | 3.7.7 | LGPL v2.1+ | Dynamically loaded JNI libraries; sources accessible | **COMPLIANT** |
| **Kotlin Standard Library** | JetBrains | 2.3.20 | Apache 2.0 | Permissive bytecode distribution | **COMPLIANT** |
| **Compose Multiplatform** | JetBrains | 1.9.3 | Apache 2.0 | Permissive UI toolkit runtime | **COMPLIANT** |
| **SQLite JDBC** | xerial/sqlite-jdbc | 3.45.1.0 | Apache 2.0 | Permissive SQLite wrapper | **COMPLIANT** |
| **JNA** | java-native-access | 5.14.0 | LGPL v2.1 / Apache 2.0 | Dual-licensed; distributed under Apache 2.0 terms | **COMPLIANT** |

---

## 3. License Notice Packaging

- All third-party copyright notices and license texts are aggregated in `docs/THIRD_PARTY_NOTICES.md` and accessible inside the application through the Settings -> "About & Licenses" dialog.
- The project's root `LICENSE` file governs Vimivo application source code.
- Zero copyleft GPL contagion or patent-encumbered codecs are present in the final binaries.
