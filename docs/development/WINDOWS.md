# Vimivo — Windows Development Guide

This guide details the Windows desktop architecture, Win32 multimedia integration, installer packaging, and build procedures.

---

## 1. Architecture Overview

- **Module Path**: `desktopApp`
- **UI Framework**: Compose Multiplatform Desktop with Material 3
- **Audio & Video Engine**: `libmpv` client API via `mpv-2.dll`
- **Video Rendering Bridge**: Direct Win32 HWND attachment (`MpvWin32Renderer`) via `wid` parameter with `vo=gpu`
- **Credential Storage**: Windows Credential Manager (`WindowsCredentialStore`) using Win32 Advapi32 APIs (`CredWriteW`, `CredReadW`, `CredDeleteW`)
- **Persistence**: SQLite version 6 stored under `%LOCALAPPDATA%\Vimivo\vimivo.sqlite`
- **Packaging**: Windows Installer (`.msi`) and standalone executable installer (`.exe`) via Compose Desktop / WiX Toolset with stable upgrade UUID

---

## 2. Prerequisites & Setup (Windows Host)

1. **Operating System**: Windows 10 or 11 (64-bit).
2. **JDK 17**: Eclipse Temurin or Amazon Corretto 17. Ensure `JAVA_HOME` is set in system environment variables.
3. **WiX Toolset**: WiX 3.11+ (required for `.msi` packaging).
4. **Native DLLs**: Ensure `mpv-2.dll` and required multimedia dependencies (FFmpeg, libass, etc.) are available in the application runtime path.

---

## 3. Build & Test Commands

On Windows PowerShell or Command Prompt:

```powershell
# Run desktop test suite
.\gradlew.bat :desktopApp:desktopTest

# Launch Compose Desktop app locally
.\gradlew.bat :desktopApp:run

# Package Windows MSI installer
.\gradlew.bat :desktopApp:packageMsi

# Package standalone Windows EXE installer
.\gradlew.bat :desktopApp:packageExe
```

Output locations:
- MSI: `desktopApp\build\compose\binaries\main\msi\Vimivo-1.0.0.msi`
- EXE: `desktopApp\build\compose\binaries\main\exe\Vimivo-1.0.0.exe`

---

## 4. Packaging Configuration & Upgrade Safety

Vimivo's Windows installer is configured in `desktopApp/build.gradle.kts`:
- **Stable Upgrade UUID**: Pinned UUID ensuring version upgrades cleanly replace older versions without creating duplicate Add/Remove Programs entries.
- **Per-User Installation**: Installs to `%LOCALAPPDATA%\Programs\Vimivo` without requiring Administrator privileges.
- **Start Menu & Desktop Shortcuts**: Configured with high-resolution multi-size `.ico` icon.
- **File Associations**: Associated with common audio and video formats (`.mp3`, `.flac`, `.m4a`, `.wav`, `.mp4`, `.mkv`).

---

## 5. Code Signing with SignTool

For release builds, sign installers with an Authenticode certificate:

```powershell
signtool sign /tr http://timestamp.digicert.com /td sha256 /fd sha256 /a `
  desktopApp\build\compose\binaries\main\msi\Vimivo-1.0.0.msi
```
