# Windows desktop architecture

Windows uses the existing Compose Desktop JVM target rather than adding an unrelated Kotlin/Native application. Shared media models, playback contracts, SQLite migrations, scanning, playlists, favorites, history, resume positions, and Compose UI remain authoritative.

## Platform selection

Desktop platform adapters are selected at runtime. Windows persistent data resolves beneath `%LOCALAPPDATA%\Vimivo`; the conventional `%USERPROFILE%\AppData\Local\Vimivo` location is used only when `LOCALAPPDATA` is unavailable. The database and caches never default to the installation or working directory.

Compose packaging selects EXE and MSI formats only on Windows. The MSI uses a stable upgrade UUID so later installers can upgrade the same product. macOS continues to select DMG and keeps its existing Application Support database path.

## Playback boundary

The libmpv JNA client API is reusable for Windows audio and network playback when a compatible `mpv-2.dll` is packaged. Cocoa rendering is now behind `DesktopVideoRenderer`; it cannot be loaded on Windows accidentally.

Windows embedded video is deliberately reported unsupported until an HWND or libmpv Render API adapter is implemented and tested on Windows. Successful DLL loading alone is not video support. Required validation includes resize, fullscreen, DPI changes, minimize/restore, rapid switching, and native cleanup.

## Native distribution blockers

This repository currently contains no audited Windows libmpv DLL set, Windows native renderer, Windows icon, signing certificate, or Windows CI runner. EXE/MSI tasks cannot be built or executed on the current macOS host. A Windows release must bundle the JVM and every required DLL, inspect dependencies, run playback on a clean Windows 10/11 x64 machine, and record artifact hashes.

Credentials must eventually use Windows Credential Manager. Plaintext SQLite or settings storage is not an acceptable fallback.
