# Vimivo — Linux Development Guide

This guide details the Linux desktop architecture, X11/XWayland multimedia integration, MPRIS 2 media controls, package generation, and runtime requirements.

---

## 1. Architecture Overview

- **Module Path**: `desktopApp`
- **UI Framework**: Compose Multiplatform Desktop with Material 3
- **Audio & Video Engine**: `libmpv` client API via `libmpv.so.1` / `libmpv.so.2`
- **Video Rendering Bridge**: Direct X11 window attachment (`MpvX11Renderer`) via XID parameter with `vo=gpu`
- **System Media Controls**: MPRIS 2 D-Bus service over the session bus with microsecond position reporting, track ID pathing, and `PropertiesChanged` signal emission
- **Credential Storage**: Freedesktop Secret Service API via `LinuxCredentialStore` (`libsecret-1.so.0`)
- **Persistence**: SQLite version 6 stored under `$XDG_DATA_HOME/Vimivo/vimivo.sqlite` (defaults to `~/.local/share/Vimivo/vimivo.sqlite`)
- **Packaging Formats**: Debian package (`.deb`), Red Hat package (`.rpm`), standalone AppImage, and Flatpak manifest

---

## 2. Prerequisites & Setup (Linux Host)

On Ubuntu / Debian:
```shell
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk libmpv-dev libsecret-1-dev libx11-dev
```

On Fedora / RHEL:
```shell
sudo dnf install -y java-17-openjdk-devel mpv-libs-devel libsecret-devel libX11-devel
```

On Arch Linux:
```shell
sudo pacman -S jdk17-openjdk mpv libsecret libx11
```

---

## 3. Build & Test Commands

```shell
# Run desktop tests
./gradlew :desktopApp:desktopTest

# Launch Compose Desktop app locally
./gradlew :desktopApp:run

# Package Debian (.deb) package
./gradlew :desktopApp:packageDeb

# Package Red Hat (.rpm) package
./gradlew :desktopApp:packageRpm
```

Output locations:
- DEB: `desktopApp/build/compose/binaries/main/deb/vimivo_1.0.0-1_amd64.deb`
- RPM: `desktopApp/build/compose/binaries/main/rpm/vimivo-1.0.0-1.x86_64.rpm`

---

## 4. AppImage & Flatpak Builds

- **AppImage**: Build script available in `scripts/build_appimage.sh` (packages JRE runtime, libmpv shared libraries, and desktop entry).
- **Flatpak**: Manifest provided at `distribution/flatpak/dev.vimivo.Player.yml` with sandboxed portal permissions for audio playback and user media folders.
- **Desktop Launcher**: Compliant Freedesktop `.desktop` file and AppStream metadata (`dev.vimivo.Player.metainfo.xml`).
