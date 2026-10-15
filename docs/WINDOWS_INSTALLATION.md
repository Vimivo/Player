# Windows Installation and Distribution Guide

Vimivo provides standalone, self-contained Windows distributions requiring no external JDK, mpv, or developer tooling.

## Target Packages

- **Vimivo Setup EXE**: Graphical installer for Windows 10/11 x64.
- **Vimivo MSI**: Standard Windows Installer database with upgrade UUID `e32d5e8d-901d-4d16-9b26-32d2fe47f890`.

## Installation Characteristics

- **Per-User Installation**: Installs to `%LOCALAPPDATA%\Programs\Vimivo` without requiring administrator elevation.
- **Start Menu & Desktop**: Registers a Start Menu shortcut under `Vimivo` and optional desktop shortcut.
- **Directory Chooser**: Allows users to customize the installation location.
- **Bundled Runtime**: Contains a custom stripped OpenJDK 17 runtime image created via jlink containing only required modules (`java.base`, `java.desktop`, `java.sql`, etc.).
- **Bundled Native Libraries**: Includes version-pinned `mpv-2.dll` and companion DLLs (FFmpeg, libass, etc.).

## Upgrade and Uninstallation Safety

- **Data Preservation**: User library database, playlists, bookmarks, and artwork caches stored in `%LOCALAPPDATA%\Vimivo` are strictly preserved across upgrades and uninstalls.
- **Zero Media Modification**: Vimivo never touches or deletes original media files during installation, playback, indexing, or uninstallation.

## Building on Windows

Prerequisites:
- Windows 10 or 11 x64
- JDK 17 (Eclipse Temurin or OpenJDK)
- Python 3.8+
- WiX Toolset 3.11+ (for MSI generation via jpackage)

Commands:
```powershell
# 1. Compile and test
./gradlew :desktopApp:compileKotlinDesktop :desktopApp:desktopTest :desktopApp:detekt

# 2. Build distributable image
./gradlew :desktopApp:createDistributable

# 3. Bundle native libmpv DLLs
python desktopApp/scripts/bundle_windows_mpv.py desktopApp/build/compose/binaries/main/app/Vimivo C:\path\to\mpv-dev-x86_64

# 4. Generate standalone EXE and MSI installers
./gradlew :desktopApp:packageExe :desktopApp:packageMsi
```

The resulting installers are output to `desktopApp/build/compose/binaries/main/exe/` and `desktopApp/build/compose/binaries/main/msi/`.
