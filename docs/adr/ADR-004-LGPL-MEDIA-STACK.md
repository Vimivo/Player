# ADR-004: LGPL-only media stack, pinned native binaries

Status: **Accepted (direction)**, implementation open. Date: 2026-10-19.

## Context

Vimivo is MIT-licensed and must be shippable through the Mac App Store, Microsoft Store, Play and App Store, and as plain
downloads, without turning the app into a GPL work. Playback needs decoders, demuxers and renderers, not encoders.

What ships today (checked 2026-10-19):

| Platform | Binary | Source | Licence in practice |
|---|---|---|---|
| macOS | libmpv 0.41.0 + FFmpeg 9.0.2 bundled by `bundle_macos_mpv.py` | Homebrew bottles, unpinned (`brew install mpv` in `macos.yml`) | **GPLv3**: FFmpeg `--enable-gpl --enable-version3 --enable-libx264 --enable-libx265`; mpv built with `gpl` and rubberband |
| Windows | libmpv-2.dll | shinchiro `mpv-dev-x86_64-v3-20261016`, SHA-256 pinned in `windows.yml` | **GPLv3** (GPL build) |
| Linux | libmpv2 + libav* from Ubuntu 24.04 | runner `apt`, unpinned, copied by `bundle_linux_mpv.py` | **GPL** (Ubuntu FFmpeg is built with `--enable-gpl`) |
| Android | LibVLC 3.7 AAR | Maven Central | LGPL-2.1+ core; plug-in set to be audited |
| iOS | none yet (MPVKit decided as fallback) | — | MPVKit ships separate LGPL and GPL products |

## Decision

1. **Desktop libmpv and FFmpeg are built LGPL-only**:
   - FFmpeg configured without `--enable-gpl`, `--enable-version3` or `--enable-nonfree`; no x264, x265, xvid,
     vidstab, frei0r or rubberband.
   - Native PNG/JPEG encoders stay for snapshots.
   - mpv is built with `-Dgpl=false`, which makes libmpv LGPL-2.1+.
2. **Every native binary is pinned by version and SHA-256**:
   - The build inputs (source tarballs) are pinned, and so are the outputs (the bundled libraries).
   - CI fails on any mismatch.
   - Applies to the macOS, Windows and Linux libmpv/FFmpeg builds, MPVKit (Swift Package with exact version plus
     checksum) and LibVLC (exact Gradle version plus Gradle dependency verification).
3. **Each OS builds its own stack in its own CI workflow** from the pinned sources, so the shipped bytes are
   reproducible and the LGPL relinking obligation is met: dynamic libraries, plus the exact sources and build scripts
   are published with each release.
4. **About shows the notices on all five apps**:
   - Vimivo's MIT licence, the LGPL-2.1 text, and the list of bundled libraries with versions and licences.
   - A link to the corresponding source for the release.
5. **Conversion (#39) uses an optional, separately downloaded encoder module**, which may contain GPL encoders.
   It is never part of the default app.

## Consequences

- **DVD and Audio CD (#2) conflict with this decision.**
  - libdvdnav/libdvdread (DVD) and libcdio-paranoia (CD) are GPL, and mpv disables them under `-Dgpl=false`.
  - Unencrypted Blu-ray stays possible (libbluray is LGPL).
  - Options: (a) drop DVD/CD from the default app; (b) ship them in the optional module from point 5; (c) accept GPL
    for desktop builds. **Needs a product decision.**
- Some mpv features are GPL-only and must be re-checked against an actual `-Dgpl=false` build before the matrix claims
  them: parts of the X11 backend, `dvbin`, `cdda`.
- Shipping the right binaries takes three source builds in CI (about 20–40 min per OS, cached by pinned hash) instead
  of downloading prebuilt GPL packages.
- Until this is implemented, **no public desktop build may be published**: today's macOS, Windows and Linux bundles are
  GPL works.

## Implementation status (2026-10-19)

| Item | Status |
|---|---|
| This ADR and the licence inventory | Done |
| LGPL FFmpeg/libmpv build scripts and CI jobs (macOS, Windows, Linux) | Not started |
| Pinning: Windows output | Done (existing) |
| Pinning: macOS, Linux, MPVKit, LibVLC verification metadata | Not started |
| About notices: Android | Exists (`VimivoAndroidApp.kt`); content to be checked against the final binaries |
| About notices: desktop, iOS | Not started |
| LibVLC plug-in licence audit | Not started |
