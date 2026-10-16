# Vimivo — macOS Desktop Development Guide

This guide details the macOS desktop architecture, libmpv multimedia integration, Objective-C++ Cocoa rendering bridge, standalone DMG packaging, and signing workflows.

---

## 1. Architecture Overview

- **Module Path**: `desktopApp`
- **UI Framework**: Compose Multiplatform Desktop with Material 3
- **Audio & Video Engine**: `libmpv` client API loaded via JNA (`com.sun.jna.NativeLibrary`)
- **Video Rendering Bridge**: In-process OpenGL / Cocoa bridge (`MpvCocoaRenderer.mm`) hosted inside Compose Multiplatform `SwingPanel`
- **Persistence**: SQLite version 6 stored under `~/Library/Application Support/Vimivo/vimivo.sqlite`
- **Packaging**: Standalone macOS application bundle (`Vimivo.app`) packaged into an installable disk image (`Vimivo-1.0.0.dmg`)

---

## 2. Prerequisites & Setup

1. **macOS Host**: macOS 12 Monterey or higher.
2. **JDK 17**: Eclipse Temurin or Amazon Corretto 17.
   *Note: Homebrew OpenJDK is suitable for compilation/testing, but Compose Desktop packaging (`packageDmg`) requires Corretto or Temurin.*
3. **Xcode Command Line Tools**: `xcode-select --install`
4. **libmpv Development Library**:
   ```shell
   brew install mpv
   ```

---

## 3. Build & Test Commands

```shell
# Run static analysis
./gradlew detekt

# Run desktop test suite (143+ tests)
./gradlew :desktopApp:desktopTest

# Launch Compose Desktop locally
./gradlew :desktopApp:run

# Specify custom libmpv path if not in standard dynamic library paths
./gradlew :desktopApp:run -Dvimivo.mpv.library=/opt/homebrew/lib/libmpv.dylib
```

---

## 4. Standalone DMG Packaging

Vimivo packages a self-contained macOS bundle containing:
- Bundled Temurin JRE runtime (zero external Java requirement)
- 50 relocatable native dynamic libraries (libmpv, FFmpeg, dav1d, libass, harfbuzz, freetype, etc.) with relative `@rpath` loader commands (zero `/opt/homebrew` dependencies)
- 512x512 High-DPI application icon (`icon.icns`)

```shell
# Package installable macOS DMG
./gradlew :desktopApp:packageDmg
# Output: desktopApp/build/compose/binaries/main/dmg/Vimivo-1.0.0.dmg
```

---

## 5. Code Signing & Notarization

Public distribution outside the Mac App Store requires signing with an Apple Developer ID Application certificate and notarization with Apple:

```shell
# Sign application bundle with Hardened Runtime
codesign --deep --force --verify --verbose --options runtime \
  --sign "Developer ID Application: Your Name (TEAM_ID)" \
  --entitlements desktopApp/entitlements.plist \
  build/compose/binaries/main/app/Vimivo.app

# Notarize disk image
xcrun notarytool submit build/compose/binaries/main/dmg/Vimivo-1.0.0.dmg \
  --keychain-profile "VimivoNotarization" --wait

# Staple notarization ticket
xcrun stapler staple build/compose/binaries/main/dmg/Vimivo-1.0.0.dmg
```
