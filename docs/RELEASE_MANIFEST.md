# Vimivo Release Manifest

Machine-readable companion: [`docs/release-manifest.json`](file:///Users/ta-shanto/Coding/products/Vimivo/docs/release-manifest.json)  
Version: `1.0.0-rc.1`  
Source Commit: `c2bf084`  
Build Date: `2026-10-16`  

---

## 1. Toolchain Specifications

| Subsystem | Verified Toolchain / Coordinate | Version |
| :--- | :--- | :--- |
| **Java Development Kit** | Eclipse Temurin OpenJDK | 17.0.14 |
| **Kotlin Multiplatform** | JetBrains Kotlin | 2.3.20 |
| **Compose Multiplatform** | JetBrains Compose Multiplatform | 1.9.3 |
| **Build Automation** | Gradle Build Tool | 8.13 |
| **Android Build Tool** | Android Gradle Plugin (AGP) | 8.13.2 |
| **Android API Target** | Compile SDK 36 / Target SDK 36 | Android 16 (Baklava) |
| **Android Min SDK** | Min SDK 26 | Android 8.0 (Oreo) |
| **Multimedia Engine (Desktop)** | libmpv / FFmpeg / libplacebo / libass | 2.0.0 (JNA dynamic loader) |
| **Multimedia Engine (Android)** | Google Media3 ExoPlayer | 1.11.1 |
| **Multimedia Engine (iOS)** | Apple AVFoundation | iOS 16.0+ native |
| **Persistence Engine** | SQLite JDBC / Native SQLite3 WAL mode | Schema v4 |

---

## 2. Release Artifact Inventory & Verification Hashes

| Platform | Format | Filename | Size (Bytes) | SHA-256 Digest | Status |
| :--- | :---: | :--- | :---: | :--- | :---: |
| **Android** | APK | `androidApp-release-unsigned.apk` | 147,577,927 | `3a123502d63bcff7cd46e52556ed01eb6048d2c1ed3214cd8160c9fcd2c0cf2b` | Built & Verified |
| **Android** | AAB | `androidApp-release.aab` | 76,079,193 | `b769278a8ae22b5bbf7681a16e447115288972215c66be600b66c71a4ec0df7f` | Built & Verified |
| **macOS** | DMG | `Vimivo-1.0.0.dmg` | 150,423,483 | `42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8` | Built & Verified |
| **Windows** | EXE | `Vimivo-Setup-1.0.0.exe` | ~145 MB | Authenticode CI pipeline | Configured |
| **Windows** | MSI | `Vimivo-1.0.0.msi` | ~145 MB | Authenticode CI pipeline | Configured |
| **Linux** | DEB | `vimivo_1.0.0_amd64.deb` | ~140 MB | Packaging CI pipeline | Configured |
| **Linux** | RPM | `vimivo-1.0.0.x86_64.rpm` | ~140 MB | Packaging CI pipeline | Configured |
| **Linux** | AppImage | `Vimivo-1.0.0.AppImage` | ~145 MB | Standalone bundler | Configured |
| **iOS** | Archive | `Vimivo.xcarchive.zip` | ~85 MB | Xcode build pipeline | Configured |
