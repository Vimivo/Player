# Cross-platform beta release audit

Audit date: 2026-10-14. Repository: `Vimivo/Player`, branch `main`, baseline commit `4374ce5`. This is an engineering status report, not a release approval.

## Evidence categories

- **IMPLEMENTED**: source code provides the behavior.
- **TESTED**: automated test evidence exists and has passed in this workspace.
- **RUNTIME VERIFIED**: exercised in a real packaged or interactive application environment.
- **PARTIAL**: only some required behavior exists.
- **BLOCKED**: cannot be completed without an external environment, dependency, or legal/release input.
- **NOT IMPLEMENTED**: no production implementation exists.

## Shared and desktop feature matrix

| Feature | macOS | Windows | Evidence / limitation |
|---|---|---|---|
| SQLite library roots and scanner | IMPLEMENTED, TESTED | IMPLEMENTED, TESTED foundation | Temporary SQLite/scanner/reconciliation tests pass. |
| Local audio libmpv playback | IMPLEMENTED | PARTIAL | macOS has a libmpv engine; no interactive audio smoke test in this audit. Windows needs a packaged `mpv-2.dll`. |
| Embedded video | PARTIAL | BLOCKED | macOS Cocoa bridge compiles but has no packaged clean-machine test. Windows renderer is deliberately unavailable pending HWND/render API work. |
| Folder picker and Finder open files | IMPLEMENTED, TESTED | PARTIAL | Buffered macOS handler tests pass. Windows Explorer integration is not implemented. |
| Library UI and search | IMPLEMENTED, TESTED | IMPLEMENTED, TESTED foundation | Real indexed list/search UI exists; albums/artists/folders are not complete desktop screens. |
| Metadata, artwork, thumbnails | PARTIAL, TESTED | PARTIAL, TESTED | Bounded Java Sound audio extraction and SQLite/library integration exist. Compressed/tagged audio, video metadata, artwork, and thumbnails remain incomplete. |
| Playlists, favorites, history, resume, bookmarks | PARTIAL, TESTED | PARTIAL, TESTED | SQLite persistence, history/resume lifecycle, and desktop personal-media UI actions are tested. Import/export, queue-backed playlist playback, and runtime interaction verification remain. |
| Desktop settings / backup and restore | PARTIAL | PARTIAL | Key/value storage exists; settings and backup user flows do not. |
| Subtitles / audio tracks | IMPLEMENTED engine API | PARTIAL | mpv track and external-subtitle calls exist; desktop control UI is incomplete. |
| Equalizer | IMPLEMENTED, TESTED | PARTIAL | Filter construction/unit tests pass; active audio-path smoke testing remains outstanding. |
| Direct network streams | IMPLEMENTED engine URI support | PARTIAL | HTTP/HTTPS/RTSP accepted by mpv engine; stream history and network browser do not exist. |
| SMB/WebDAV/SFTP/FTP/UPnP | NOT IMPLEMENTED | NOT IMPLEMENTED | Models only; no maintained desktop clients or credential store. |
| Native packaging | BLOCKED | BLOCKED | See below. |

## Build and test evidence

Passed in this workspace:

- `:desktopApp:compileKotlinDesktop`
- `:desktopApp:desktopTest`
- `:desktopApp:detekt`
- root `detekt`

The combined Android debug build and unit-test request stalled at `:androidApp:kspDebugKotlin` with no CPU activity and was interrupted after several minutes. It is **inconclusive**, not a passing result. Android instrumentation tests were not run because no emulator/device was provided.

`./gradlew :desktopApp:packageDmg` was executed previously and failed at Compose `checkRuntime`: the installed Homebrew JDK is rejected for reliable runtime-image packaging. No DMG, EXE, or MSI was produced during this audit.

## Release blockers

1. macOS distribution still loads libmpv and the renderer bridge from absolute Homebrew paths. The required transitive dylibs are not bundled, audited, signed, or clean-machine tested.
2. A supported packaging JDK is absent; the Compose packaging safety check must not be disabled as a release workaround.
3. Windows has no native libmpv DLL bundle, HWND/render-API adapter, Windows runner, installer validation, Explorer integration, or Credential Manager implementation.
4. Production desktop metadata/artwork/thumbnails, backup/restore, persistent settings UI, playlist/favorite/bookmark UI, and network-storage implementations are unfinished.
5. The LibVLC redistribution/source-replacement obligations described in `LICENSE_AUDIT.md` require legal review before an Android release.
6. Signing, notarization, and code-signing credentials are not configured. No release signing claim is made.

## Beta conclusion

Vimivo is not ready for a cross-platform public beta. The current work is a tested foundation with selected macOS development features. The next release-critical sequence is: complete desktop playback/library integration, establish an auditable standalone macOS native dependency bundle, implement and validate the Windows renderer/DLL bundle on a Windows runner, then execute clean-machine installer and media tests.
