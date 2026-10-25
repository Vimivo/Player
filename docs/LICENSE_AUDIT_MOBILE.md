# Mobile media-stack licence audit (ADR-004)

> **Resolved for Android (2026-10-26):** LibVLC was removed and replaced by Vimivo's own LGPL libmpv/FFmpeg build ([ADR-005](adr/ADR-005-MOBILE-MEDIA-ENGINE.md)). The findings below are kept as the audit that led to that decision. The iOS findings still stand.

Date: 2026-10-19. Scope: the native media libraries in the Android app (LibVLC) and in the iOS fallback decided
earlier (MPVKit). Question: does either make Vimivo a GPL work, and does it meet the LGPL relinking requirement?

## Android — `org.videolan.android:libvlc-all:3.7.7`

**How it is linked.** The AAR ships three libraries per ABI:

- `libvlcjni.so` (97 KB): the JNI glue.
- `libvlc.so` (45.9 MB on arm64-v8a): libvlc, libvlccore, every VLC module and the contribs (FFmpeg, libass, …),
  all **statically** inside one shared library.
- `libc++_shared.so`.

Linking `libvlc.so` dynamically is enough for relinking: a user can replace the whole file. Per-component
replacement is not possible, but the LGPL does not require it.

**What is inside.** Upstream's build script was unreachable (code.videolan.org serves an anti-bot page to scripts,
and the GitHub mirror tags stop at 3.7.2). So the evidence comes from the binary itself, `jni/arm64-v8a/libvlc.so`:

| Evidence in the binary | Component | Licence |
|---|---|---|
| Module description "H.264/MPEG-4 Part 10/AVC encoder (x264)" | x264 encoder (`--enable-sout`) | **GPL-2.0-or-later** |
| Module descriptions "DVDnav Input", "DVDRead Input"; embedded configure line `--enable-dvdread` | libdvdnav, libdvdread | **GPL-2.0-or-later** |
| Strings from libpostproc | FFmpeg postproc | **GPL-2.0-or-later** |
| String "x265" | x265 references | GPL-2.0-or-later (if linked) |
| Embedded text "GNU General Public License" | — | — |

The configure line also shows `--disable-faad --disable-dca --disable-mad --disable-vcd --disable-goom
--disable-projectm`. So VideoLAN removed some GPL modules, but not x264, DVD or postproc.

**Conclusion.** The LibVLC that Vimivo ships today is a GPL combination. The Android app cannot ship under MIT
with it.

**Options:**

| Option | What it takes | Effect |
|---|---|---|
| A. Build our own LibVLC | VLC-Android's `compile-libvlc.sh` with `--disable-sout --disable-dvdread --disable-dvdnav --disable-postproc`, no x264/x265 contribs. Pinned like `scripts/build_media`, published in our Release, verified like `verify.py`. | Keeps today's codec and container coverage and the LibVLC fallback path. A large NDK build: about 1 h per ABI in CI. |
| B. Drop LibVLC | Media3 alone, plus Media3's FFmpeg decoder extension built LGPL-only (audio decoders) from `scripts/build_media`. | Smaller app and one engine. Loses LibVLC's fallback for exotic containers (#7 in the gap list) and subtitle formats Media3 does not parse. |
| C. Keep it, ship GPL | — | Conflicts with ADR-004 and app-store distribution. Rejected. |

**Recommendation: A.** It changes no behaviour, and the build fits the existing pinned-source pipeline. This is a
product decision for the next phase. Until then, no public Android build may ship with LibVLC. Media3 alone covers
the common formats.

## iOS — MPVKit 1.0.0 (2026-07-25)

**What it ships.** Two SwiftPM products: `MPVKit` (described as LGPL) and `MPVKit-GPL`.

**How it is linked.**

- Every binary target is an xcframework of **static archives** ("current ar archive"): Libmpv, Libavcodec and the
  other FFmpeg libraries, libass, libplacebo, and so on.
- Static linking into an App Store binary is compatible with the LGPL only if users get the object files needed to
  relink.
- In practice that is impossible for a signed App Store app. The relinked app cannot be installed without the
  developer's signing identity.

**What the "LGPL" product depends on:**

- gnutls (LGPL-2.1+), plus gmp, nettle and hogweed. These are dual-licensed LGPL-3.0+ / GPL-2.0+, so the LGPLv3
  option applies, and its installation-information rules conflict with App Store distribution.
- MoltenVK, shaderc and libdovi.
- OpenSSL.

**Conclusion.** MPVKit, even its LGPL product, is not suitable for an LGPL-compliant App Store app as packaged. The
iOS fallback must be built by us:

- Extend `scripts/build_media` with an `ios` target that builds FFmpeg (LGPL, `--disable-gpl --disable-version3`),
  libass, libplacebo and libmpv (`-Dgpl=false`) as **dynamic frameworks** (`.framework` in an xcframework, embedded
  and signed).
- Use the system TLS (`--enable-securetransport`) instead of gnutls.
- Leave out the LGPLv3/GPL math libraries.
- Dynamic frameworks are allowed on iOS. The App Store binary then contains replaceable libraries, which is how VLC
  for iOS and other LGPL apps comply.

Until then, iOS keeps AVPlayer only (system frameworks, no third-party media code).

## Summary

| App | Media library | Linking | GPL inside | Status |
|---|---|---|---|---|
| Android | LibVLC 3.7.7 (Maven) | dynamic `libvlc.so`, modules static inside | yes (x264, DVD, postproc) | Blocker for public release; option A recommended |
| iOS | AVPlayer (system) | system framework | no | OK |
| iOS fallback | MPVKit 1.0.0 | static xcframeworks | not in the LGPL product, but gnutls/gmp/nettle (LGPLv3) and static linking | Not usable; build our own dynamic frameworks |
| macOS, Windows, Linux | `scripts/build_media` libmpv + FFmpeg | dynamic | no (verified by `verify.py`) | See ADR-004 |
