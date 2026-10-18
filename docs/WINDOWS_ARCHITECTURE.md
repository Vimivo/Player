# Windows desktop architecture

Windows uses the existing Compose Desktop JVM target rather than an unrelated Kotlin/Native application. Shared media models, playback contracts, SQLite migrations, scanning, playlists, favorites, history, resume positions, and Compose UI remain authoritative across all desktop targets.

## Platform selection & storage

Desktop platform adapters are selected at runtime via `DesktopPlatform.detect()`:
- Windows persistent data resolves beneath `%LOCALAPPDATA%\Vimivo` (`%USERPROFILE%\AppData\Local\Vimivo` fallback when `LOCALAPPDATA` is not set).
- Structure:
  - Database: `%LOCALAPPDATA%\Vimivo\vimivo.sqlite`
  - Cache: `%LOCALAPPDATA%\Vimivo\cache\`
  - Artwork: `%LOCALAPPDATA%\Vimivo\cache\artwork\`
  - Thumbnails: `%LOCALAPPDATA%\Vimivo\cache\thumbnails\`
- The media library never modifies or deletes original media files. User databases are preserved across updates and uninstall.

## Native libmpv & video rendering

- **JNA Bridge**: The libmpv client API (`mpv-2.dll`, `libmpv-2.dll`, `libmpv.dll`, `mpv.dll`) is loaded via `MpvNative.load()`.
- **Bundled DLL Resolution**: `MpvNative.findBundledWindowsDllDir()` inspects the installation directories (`app/`, root, `bin/`, `runtime/bin`) and safely adds the discovered directory to `jna.library.path` using Windows path delimiters (`;`), ensuring companion DLLs (FFmpeg, libass, etc.) are loaded without PATH pollution or DLL hijacking risks.
- **Embedded Video Renderer**: `MpvWin32Renderer` obtains the native Win32 `HWND` from the Compose/AWT `Canvas` peer via `com.sun.jna.Native.getComponentID(canvas)` and configures libmpv with `vo = gpu` and property `wid = <hwnd>`. Headless environments fail cleanly with descriptive errors rather than native segfaults.
- **Engine Capabilities**: `MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS)` reports `localAudio = true`, `localVideo = true`, `hdr = true`, `embeddedSubtitles = true`, `externalSubtitles = true`.

## Secure credential management

- **Windows Credential Manager**: Sensitive remote stream credentials (HTTP Basic/Bearer, WebDAV, RTSP) are stored using Win32 `Advapi32` APIs (`CredWriteW`, `CredReadW`, `CredDeleteW`, `CredFree`) under target names `Vimivo:<key>`.
- **In-Memory Fallback**: When running in non-Windows test runners or environments where `Advapi32` is unavailable, `MemoryCredentialStore` provides process-scoped session-only storage that is wiped on exit. Passwords and secrets are never committed to plaintext SQLite or settings files.

## Desktop integration

- **File Associations & Explorer**: Supported audio and video extensions are associated with Vimivo. Command-line file arguments (`args`) and AWT `Desktop.setOpenFileHandler` route into `PlaybackQueueController` for continuous unified playback.
- **Drag and Drop**: `java.awt.dnd.DropTarget` attached to the Compose desktop `Window` accepts dropped audio and video files or folders.
- **Keyboard Shortcuts**: MenuBar items expose standard desktop shortcuts:
  - `Ctrl+O`: Open Media
  - `Ctrl+Shift+O`: Add Folder
  - `Space`: Play / Pause toggle
  - `Left / Right`: Seek backward / forward 10 seconds

## Standalone packaging (EXE & MSI)

- **Compose Desktop nativeDistributions**: Configured in `desktopApp/build.gradle.kts` targeting `TargetFormat.Exe` and `TargetFormat.Msi`.
- **Parameters**:
  - Stable Upgrade UUID: `e32d5e8d-901d-4d16-9b26-32d2fe47f890`
  - Menu group: `Vimivo`
  - Desktop shortcut: `true`
  - Start menu shortcut: `true`
  - Directory chooser: `true`
  - Per-user install: `true`
  - Application icon: `desktopApp/src/desktopMain/resources/windows/icon.ico`
- **Native Dependency Bundling**: `desktopApp/scripts/bundle_windows_mpv.py` bundles `mpv-2.dll` and companion DLLs into the app image and validates the 64-bit AMD64 PE header.
- **CI Pipeline**: `.github/workflows/windows.yml` runs compilation, detekt, desktop tests, libmpv bundling, and builds genuine signed/checksummed EXE and MSI installer packages on `windows-latest`.

## Media controls, file associations and native libraries (2026-10-18)

- **System Media Transport Controls.** `WindowsMediaControlsService` publishes title, artist, album, timeline, and
  play state to the Windows media flyout, lock screen, and hardware media keys, and handles Play, Pause, Next,
  Previous, and seek requests. It uses the C++/WinRT bridge `native/windows/VimivoMediaControls.cpp`
  (`vimivo_media_controls.dll`), built by `:desktopApp:windowsMediaControlsBridge` inside a Visual Studio developer
  environment and bundled next to libmpv (ADR-024). It is compiled in CI but has not yet been exercised on a Windows
  desktop; that check is on the release checklist.
- **File associations.** The EXE/MSI installers register Vimivo as an "Open with" handler for the audio and video
  types listed in `desktopApp/build.gradle.kts`. Windows does not let installers set themselves as the default player
  (since Windows 8); users choose Vimivo in Settings › Apps › Default apps.
- **libmpv.** CI downloads a pinned `mpv-dev-x86_64` release from `shinchiro/mpv-winbuild-cmake`, verifies its
  SHA-256 against the digest GitHub publishes, and fails if `libmpv-2.dll` is missing from the archive or from the
  distributable. The previous SourceForge download returned an HTML stub without failing the build.
- **Smoke test.** CI launches the packaged `Vimivo.exe --smoke-test …` and reads `VIMIVO_SMOKE_REPORT`, because GUI
  executables built by `jpackage` have no console attached.
