# macOS distribution

Vimivo's initial macOS distribution target is a directly distributed application and DMG. A release must bundle a Java runtime and every non-system native playback dependency so users do not need Homebrew, mpv, or Java.

## Current verified state

On 2026-10-15 (Milestone 16), the macOS packaging and native bundling pipeline was completed and verified:

1. **Self-Contained JVM Runtime**: Compose native distribution bundles OpenJDK 17 with required runtime modules (`java.base`, `java.desktop`, `java.sql`, `java.instrument`, `java.logging`, `java.prefs`, `java.xml`, `jdk.unsupported`, `jdk.crypto.ec`).
2. **Bundled Native Libraries**: 50 native dylibs (libmpv 0.41.0, FFmpeg 9.0.2, libass, libplacebo, dav1d, etc.) and `libvimivo_mpv_cocoa.dylib` are bundled into `Vimivo.app/Contents/Frameworks/`.
3. **Relocatable Install Names**: All dylib IDs and dependency references are rewritten to `@rpath` and `@loader_path` via `install_name_tool`. Verification with `otool -L` confirms **zero `/opt/homebrew` references**.
4. **Native Loader Hardening**: `MpvNative` and `MpvCocoaRenderer` automatically inspect `Contents/Frameworks` first and set `jna.library.path`, removing reliance on external tools or environment variables.
5. **Standalone Application & DMG**: `./gradlew :desktopApp:packageDmg` generates `build/compose/binaries/main/dmg/Vimivo-1.0.0.dmg` (149 MB, SHA-256: `8111c9dc81135e225b668beb5b86facaeaa20b8563951fe75ad42642afd4bade`), mountable and installable on Apple Silicon Macs without requiring Homebrew or Java.

## Release status

Development and ad-hoc signed standalone DMG distribution is **IMPLEMENTED and VERIFIED**. Public distribution via Gatekeeper remains blocked on Apple Developer ID certificate signing and `xcrun notarytool` notarization. Intel (x86_64) validation remains separate as tests were conducted on Apple Silicon arm64.
