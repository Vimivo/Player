# Linux desktop architecture

Linux reuses the existing Compose Desktop JVM application, shared media contracts, SQLite schema, scanner, and libmpv client API. It does not introduce a separate application or database.

## Persistent paths

Linux data uses `XDG_DATA_HOME/vimivo` and cache uses `XDG_CACHE_HOME/vimivo`. When the XDG variables are absent, Vimivo uses `~/.local/share/vimivo` and `~/.cache/vimivo`. The SQLite database is in the data directory and caches are separate. Vimivo never defaults to the install directory, working directory, or temporary directory.

## Packaging configuration

On a Linux host, Compose Desktop is configured to select DEB, RPM, and AppImage outputs. macOS continues to select DMG; Windows continues to select EXE/MSI. Package tasks must run on a Linux CI runner or Linux development machine, because the current macOS host cannot build or install-test Linux artifacts.

## Native playback boundary

JNA can resolve `libmpv.so` when a compatible Linux libmpv is available. The macOS Cocoa renderer is isolated and cannot load on Linux. Linux embedded video is not currently implemented: it needs an audited Wayland/X11-compatible libmpv render API or native-window adapter, exercised with resize, fullscreen, HiDPI, driver, and shutdown tests. Therefore Linux video capability remains disabled rather than claiming audio-only libmpv initialization as video support.

Linux releases also need a bundled, license-audited libmpv dependency set, a supported bundled JVM, desktop launcher, AppStream metadata, file associations, MPRIS integration, and clean-install testing on Ubuntu/Debian and Fedora. None of these artifacts have been produced or verified from the current macOS environment.
