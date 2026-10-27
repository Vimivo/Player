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

Updated 2026-10-18 (Phase P1):

- **Per-architecture DMGs.** `macos.yml` builds `Vimivo-<version>-arm64.dmg` on `macos-15` and `Vimivo-<version>-x86_64.dmg` on `macos-15-intel`. A single universal DMG is not feasible with Compose Desktop, because `jpackage` bundles an architecture-specific Java runtime (ADR-022).
- **Homebrew prefix.** The bridge build and `scripts/bundle_macos_mpv.py` use `VIMIVO_MPV_PREFIX` (defaults: `/opt/homebrew` on Apple Silicon, `/usr/local` on Intel), and verification fails if any bundled dylib still references that prefix or lacks the runner's architecture.
- **Signing and notarization.** Set these repository secrets to enable Developer ID signing, notarization, and stapling; without them CI produces ad-hoc signed DMGs:

  | Secret | Value |
  |---|---|
  | `MACOS_CERTIFICATE_P12` | Base64 of the Developer ID Application certificate (.p12) |
  | `MACOS_CERTIFICATE_PASSWORD` | Password for that .p12 |
  | `MACOS_SIGN_IDENTITY` | e.g. `Developer ID Application: Name (TEAMID)` |
  | `MACOS_NOTARY_APPLE_ID`, `MACOS_NOTARY_PASSWORD`, `MACOS_NOTARY_TEAM_ID` | Apple ID, app-specific password, and team for `notarytool` |

  Locally, export `VIMIVO_MAC_SIGN_IDENTITY` before `./gradlew :desktopApp:packageDmg` to sign with the hardened runtime and `desktopApp/macos/Vimivo.entitlements`.
- **File associations.** The packaged `Info.plist` declares 23 audio and video document types as an alternate handler, so Vimivo appears in "Open With" without taking over the default player.
- **Smoke test.** Every CI build launches the packaged app with `--smoke-test` and requires local MP4, MKV, MP3, and FLAC playback to advance (ADR-025). Verified locally on Apple Silicon: 4/4 PASS from `Vimivo.app/Contents/MacOS/Vimivo`.
- **Not yet verified:** a notarized build (no Developer ID secrets are configured) and the x86_64 DMG, which only the `macos-15-intel` runner can produce.
