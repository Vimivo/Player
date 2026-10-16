# Vimivo — Five-Platform Distribution Matrix

**Milestone**: 50  
**Target Release**: `1.0.0-rc.1`  
**Git Commit SHA**: `3924266`  
**Date**: 2026-10-16  

---

## 1. Distribution Artifacts Summary

| Platform | Format | Filename | Size (Bytes) | SHA-256 Checksum | Signing Status | Verification Method |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android** | `APK` | `androidApp-release-unsigned.apk` | 147,577,927 | `7425f12d6a8bd450bfe88420c4ad2137f0820e0bcc0e35b1046efdcee65c92b3` | Unsigned (Ready for Play Keystore) | Zip entries, classes.dex, ABIs inspected |
| **Android** | `AAB` | `androidApp-release.aab` | 76,081,890 | `8d7f0060a9ec770bb5162d0f5d61ed90f5cd7226a67ef64216030c8710af7133` | Unsigned (Play App Signing) | Base manifest, lib splits inspected |
| **macOS** | `DMG` | `Vimivo-1.0.0.dmg` | 151,452,869 | `e6ab2791a0a660dc75e726906df2dab506350a66e43dd9e97551faf19829146c` | Ad-Hoc Signed (Hardened Runtime) | Mounted via `hdiutil`, codesign verified |
| **Windows** | `EXE` | `Vimivo-Setup-1.0.0.exe` | CI Generated | Managed via CI Manifest | Authenticode Ready | Configured in `desktopApp/build.gradle.kts` |
| **Windows** | `MSI` | `Vimivo-1.0.0.msi` | CI Generated | Managed via CI Manifest | Authenticode Ready | WiX upgrade UUID configured |
| **Linux** | `DEB` | `vimivo_1.0.0_amd64.deb` | CI Generated | Managed via CI Manifest | Unsigned standard deb | Debian package control audited |
| **Linux** | `RPM` | `vimivo-1.0.0.x86_64.rpm` | CI Generated | Managed via CI Manifest | Unsigned standard rpm | RPM spec file audited |
| **Linux** | `AppImage` | `Vimivo-1.0.0.AppImage` | CI Generated | Managed via CI Manifest | Unsigned executable | Single-file portable bundle |
| **iOS** | `XCARCHIVE` | `Vimivo.xcarchive.zip` | CI Generated | Managed via CI Manifest | Developer Team Ready | SwiftUI & AVKit framework integration |

---

## 2. Platform Installation & Upgrade Protocols

### 2.1 macOS DMG Installation
1. Download `Vimivo-1.0.0.dmg`.
2. Double-click to mount disk image.
3. Drag `Vimivo.app` to `/Applications`.
4. Launch `Vimivo.app`. The application contains all runtime dependencies (`Contents/Frameworks/` contains all 50 dylibs) and requires no Homebrew installation.
5. Upgrade: Replacing `/Applications/Vimivo.app` with a newer version preserves all user databases under `~/Library/Application Support/Vimivo/`.

### 2.2 Android Installation & Upgrade
1. Clean Sideload: `adb install -r androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk` (once signed with debug or test key).
2. Play Store: Upload `androidApp-release.aab` to Play Console internal testing track.
3. Upgrade: Android Package Manager preserves app data under `/data/data/dev.vimivo.player/` including Room SQLite database and DataStore preferences.

### 2.3 Windows Installation & Upgrade
1. Run `Vimivo-Setup-1.0.0.exe` or `msiexec /i Vimivo-1.0.0.msi`.
2. Installs to `%ProgramFiles%\Vimivo\`.
3. Persistent user data resides in `%APPDATA%\Vimivo\`.
4. Upgrades match the fixed upgrade code `9c4a8f10-1845-4e3a-95bc-7023c7f12e8b`, replacing binaries without touching user databases.

### 2.4 Linux Installation & Upgrade
1. Ubuntu / Debian: `sudo dpkg -i vimivo_1.0.0_amd64.deb`
2. Fedora: `sudo rpm -Uvh vimivo-1.0.0.x86_64.rpm`
3. AppImage: `chmod +x Vimivo-1.0.0.AppImage && ./Vimivo-1.0.0.AppImage`
4. Persistent data stored in `~/.local/share/vimivo/` adhering to XDG Base Directory specification.
