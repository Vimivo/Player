# Vimivo System Requirements Specification

## 1. Overview

Vimivo is engineered to be lightweight, fast, and resource-efficient across mobile and desktop environments.
This document outlines the minimum and recommended hardware, operating system, and graphics specifications for all five target platforms.

---

## 2. Platform Requirements Matrix

### 2.1 Android (Phone & Tablet)
- **Minimum OS**: Android 9.0 Pie (API level 28)
- **Target OS**: Android 16 (API level 36)
- **Supported Architectures**: `arm64-v8a` (64-bit ARM), `armeabi-v7a` (32-bit ARM), `x86_64` (Emulators/Chromebooks)
- **RAM**: Minimum 2 GB; Recommended 4 GB+
- **Storage**: Minimum 200 MB free device storage for application and thumbnail caching
- **Permissions**:
  - Android 13+: `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `POST_NOTIFICATIONS`
  - Android 9–12: `READ_EXTERNAL_STORAGE`
  - Optional: `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for uninterrupted background listening

### 2.2 iOS & iPadOS
- **Minimum OS**: iOS 16.0 / iPadOS 16.0
- **Target OS**: iOS 18 / iPadOS 18
- **Supported Hardware**: iPhone 11 and newer, iPad (8th generation) and newer, iPad Air (4th gen+), iPad Pro (all Apple Silicon models)
- **RAM**: Minimum 3 GB
- **Storage**: Minimum 150 MB free storage

### 2.3 macOS Desktop
- **Minimum OS**: macOS 12.0 Monterey
- **Recommended OS**: macOS 14 Sonoma or macOS 15 Sequoia
- **Supported Architectures**: Apple Silicon (M1, M2, M3, M4) native; Intel (x86_64) supported via Rosetta 2 or dedicated build
- **RAM**: Minimum 4 GB; Recommended 8 GB+
- **Storage**: Minimum 350 MB free storage (application bundle size ~143 MB + local library index)
- **Graphics / Display**: OpenGL 3.3+ or Metal-compatible GPU; Retina / ProMotion displays fully supported

### 2.4 Windows Desktop
- **Minimum OS**: Windows 10 64-bit (Version 2004, Build 19041 or higher) or Windows 11 (all versions)
- **Supported Architecture**: 64-bit Intel/AMD (`x64`)
- **RAM**: Minimum 4 GB; Recommended 8 GB+
- **Storage**: Minimum 350 MB free disk space on system drive
- **Graphics / Display**: DirectX 11 / Direct3D 11 / OpenGL 3.3 compatible GPU; High-DPI scaling fully supported

### 2.5 Linux Desktop
- **Minimum OS / Toolchain**: Any modern 64-bit Linux distribution with GNU C Library (`GLIBC`) 2.35 or newer:
  - Ubuntu 22.04 LTS, 24.04 LTS
  - Debian 12 (Bookworm) or newer
  - Fedora 38, 39, 40 or newer
  - Arch Linux (current)
- **Display Server**: X11 native; Wayland compositors (GNOME Wayland, KDE Plasma Wayland, Sway) supported via XWayland
- **Core Native Libraries**: `libmpv.so.2` (libmpv2), `libsecret-1.so.0` (Secret Service), `libdbus-1.so.3` (D-Bus for MPRIS)
- **RAM**: Minimum 4 GB
- **Storage**: Minimum 350 MB free space
