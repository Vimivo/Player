# Linux desktop architecture

Vimivo on Linux reuses the existing Compose Desktop JVM application, shared media domain contracts, SQLite database schema, scanner, metadata extractors, and the libmpv client API. It maintains strict platform parity without duplicating core business logic.

## 1. Directory and storage layout

Linux persistent paths strictly comply with the XDG Base Directory Specification:
- **Data directory**: `$XDG_DATA_HOME/vimivo` (default: `~/.local/share/vimivo`)
  - Contains the authoritative SQLite database (`vimivo.db`), migration logs, and persistent settings.
- **Cache directory**: `$XDG_CACHE_HOME/vimivo` (default: `~/.cache/vimivo`)
  - Contains bounded embedded audio artwork and video frame thumbnail caches.
- **Config directory**: `$XDG_CONFIG_HOME/vimivo` (default: `~/.config/vimivo`)
  - Reserved for application and user configurations.
- **State directory**: `$XDG_STATE_HOME/vimivo` (default: `~/.local/state/vimivo`)
  - Local runtime diagnostics and application state.

Overrides must be absolute paths; relative overrides are rejected and fallback to default user paths. Vimivo never writes user databases to temporary directories or package install roots.

## 2. Native multimedia playback

Vimivo uses libmpv (`libmpv.so.2` / `libmpv.so`) via JNA in-process:
- **Embedded video rendering**: `MpvEglRenderer` renders through the libmpv render API into Vimivo's own EGL context on the AWT `Canvas`'s X11/XWayland window (JNA only, no compiled bridge). The LGPL libmpv (ADR-004) has no X11 output (mpv's X11 code is GPL), so `wid` embedding is not possible; VA-API decodes through a DRM render node. See `LINUX_NATIVE_PLAYBACK.md`.
- **Audio output**: Handled natively by libmpv, interfacing automatically with PipeWire, PulseAudio, or ALSA depending on the active Linux distribution.
- **Engine capabilities**: On Linux, `MpvPlayerEngine` reports `localVideo = true` and `hdr = true`.

## 3. MPRIS 2 D-Bus media integration

Vimivo registers the standard MPRIS 2 service on the Linux Session Bus:
- **Well-known bus name**: `org.mpris.MediaPlayer2.vimivo`
- **Object path**: `/org/mpris/MediaPlayer2`
- **Interfaces implemented**:
  - `org.mpris.MediaPlayer2`: `Identity`, `DesktopEntry`, `SupportedUriSchemes`, `SupportedMimeTypes`, `CanQuit`, `CanRaise`, `HasTrackList`.
  - `org.mpris.MediaPlayer2.Player`: `PlaybackStatus`, `LoopStatus`, `Rate`, `Shuffle`, `Metadata`, `Volume`, `Position`, `CanControl`, `CanPlay`, `CanPause`, `CanSeek`, `CanGoNext`, `CanGoPrevious`.
  - Methods: `Play`, `Pause`, `PlayPause`, `Stop`, `Next`, `Previous`, `Seek`, `SetPosition`, `OpenUri`.
- **Property mapping**: Microseconds for time/position, D-Bus object path track identifiers (`/dev/vimivo/track/<id>`), and reactive `PropertiesChanged` signal emission.
- **Fallback**: If `libdbus-1.so.3` is unavailable (e.g. headless or mock test environments), `MprisServiceFactory` seamlessly falls back to `NoOpMprisService`.

## 4. Secure credential storage

Vimivo integrates with the Freedesktop Secret Service API via `LinuxCredentialStore`:
- Dynamically loads `libsecret-1.so.0` via JNA.
- Stores streaming credentials and authentication tokens in the system keyring (GNOME Keyring, KWallet / KeePassXC secret service) under schema `dev.vimivo.NetworkCredential`.
- Zero plaintext credential storage in SQLite, configuration files, or logs.
- Safe in-memory fallback (`MemoryCredentialStore`) for headless runs or when Secret Service daemon is absent.

## 5. Desktop environment integration

- **Launcher**: Standard freedesktop `.desktop` file (`dev.vimivo.desktop.desktop`) with category `AudioVideo;Audio;Video;Player;`.
- **AppStream**: Valid AppStream metadata specification (`dev.vimivo.desktop.metainfo.xml`).
- **Icons**: Official multi-resolution PNG icons (16x16 through 512x512) compliant with the Hicolor icon theme specification.
- **Keyboard shortcuts & Drag-and-Drop**: Supports native `Ctrl+O` (open file), `Ctrl+Shift+O` (open directory), `Space` (play/pause), `Left`/`Right` (seek), and file Drag-and-Drop into the main playback queue.

## 6. Standalone packaging

Four packaging formats are supported:
1. **Debian package (`.deb`)**: Configured via Compose Desktop `nativeDistributions` for Debian, Ubuntu, Linux Mint, and Pop!_OS.
2. **RPM package (`.rpm`)**: Configured for Fedora, Red Hat Enterprise Linux, openSUSE, and Rocky Linux.
3. **AppImage**: Bundled via AppDir with bundled JVM, relocatable libmpv, `AppRun`, desktop entry, and icon.
4. **Flatpak**: Sandboxed manifest (`dev.vimivo.desktop.json`) with XDG Desktop Portal FileChooser, PipeWire/PulseAudio socket, Wayland/X11 sockets, and MPRIS bus permissions.
