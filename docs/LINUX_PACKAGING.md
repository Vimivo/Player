# Linux packaging and standalone distribution

Vimivo supports four standalone Linux distribution formats designed to deliver a zero-external-dependency experience without requiring separate Java or mpv installations.

## 1. Distribution formats

| Format | Target systems | Packaging mechanism | Installation directory |
|---|---|---|---|
| **DEB** | Ubuntu, Debian, Mint, Pop!_OS | Compose Desktop `nativeDistributions { targetFormats(TargetFormat.Deb) }` | `/opt/vimivo` |
| **RPM** | Fedora, RHEL, openSUSE | Compose Desktop `nativeDistributions { targetFormats(TargetFormat.Rpm) }` | `/opt/vimivo` |
| **AppImage** | Any x86_64 Linux with glibc 2.31+ | Dedicated `AppDir` toolchain via `desktopApp/scripts/build_appimage.sh` | Portable single executable |
| **Flatpak** | Sandboxed desktop distributions | Flatpak manifest `dev.vimivo.desktop.json` (org.freedesktop.Platform 23.08) | Sandboxed `/app` |

## 2. Bundled Java runtime modules

The bundled JVM is produced by `jlink` during Gradle packaging and includes only the necessary modules:
- `java.base`, `java.desktop`, `java.sql`, `java.logging`, `java.naming`, `java.management`
- `jdk.unsupported` (required by high-performance bytecode and memory operations)
- `jdk.crypto.ec` (required for HTTPS/TLS communication with remote streaming sources)

No separate JRE or JDK is required on the user's host system.

## 3. Bundled native libraries

Standalone distribution packages bundle relocatable native dependencies using `desktopApp/scripts/bundle_linux_mpv.py`:
- `libmpv.so.2` and required multimedia codec libraries (FFmpeg / libavcodec, libavformat, libswscale, libswresample, libplacebo).
- ELF binaries are verified for 64-bit architecture (`EM_X86_64`) and safe relative RPATH/RUNPATH configuration (`$ORIGIN` and `$ORIGIN/../lib`).
- Native loading in `MpvNative` dynamically discovers and prioritizes the application's private bundled directory before querying host paths.

## 4. Desktop metadata specifications

- **Desktop entry**: `desktopApp/src/desktopMain/resources/linux/dev.vimivo.desktop.desktop`
  - Registered categories: `AudioVideo;Audio;Video;Player;`
  - MIME types: Comprehensive list covering `.mp3`, `.flac`, `.m4a`, `.wav`, `.ogg`, `.opus`, `.mp4`, `.mkv`, `.webm`, etc.
- **AppStream Metainfo**: `desktopApp/src/desktopMain/resources/linux/dev.vimivo.desktop.metainfo.xml`
  - Validated according to AppStream 0.12+ standard for GNOME Software and KDE Discover.
- **Icons**: Multi-resolution icons installed into `hicolor/<size>x<size>/apps/dev.vimivo.desktop.png` (16, 32, 48, 64, 128, 256, 512 px).

## 5. Continuous Integration (CI/CD)

The GitHub Actions workflow `.github/workflows/linux.yml`:
1. Runs on `ubuntu-latest`.
2. Validates Java 17 and Gradle build configuration.
3. Compiles all desktop source sets and runs the 64-test desktop suite and 14-test shared suite.
4. Executes Detekt static analysis.
5. Bundles native libmpv dependencies and produces standalone packages (`.deb`, `.rpm`, `.AppImage`).
6. Generates cryptographic SHA-256 checksums and publishes the build artifacts.
