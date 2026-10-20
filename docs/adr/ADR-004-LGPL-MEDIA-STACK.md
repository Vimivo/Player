# ADR-004: LGPL-only media stack, pinned native binaries

Status: **Accepted; implemented for macOS, Windows and Linux.** Android and the iOS fallback are open (see
"Mobile"). Date: 2026-10-19, updated 2026-10-20.

## Context

Vimivo is MIT-licensed and must be shippable through the Mac App Store, Microsoft Store, Play and App Store, and as
plain downloads, without becoming a GPL work. Playback needs decoders, demuxers and renderers, not encoders.

Before this decision every desktop build was GPL:

| Platform | Old libmpv/FFmpeg source | Licence |
|---|---|---|
| macOS | Homebrew bottles, unpinned. The Intel job never finished (no bottles). | GPLv3: x264, x265, rubberband |
| Windows | shinchiro GPL build | GPLv3 |
| Linux | Ubuntu `libmpv2`/`libav*` from the runner, unpinned | GPL |

## Decision

1. **Desktop libmpv and FFmpeg are built LGPL-only**, from source, by `scripts/build_media`:
   - FFmpeg is configured with `--disable-gpl --disable-nonfree --disable-version3`.
   - mpv is built with `-Dgpl=false`.
   - The build has no x264, x265, rubberband, libdvdnav/libdvdread or libcdio.
2. **Every input is pinned by version and SHA-256** in `manifest.json`: sources, staged submodules and build tools.
   `build.py` refuses anything else.
3. **Every output is pinned too.**
   - CI (`build-media.yml`) builds the three packages and verifies each on its own OS.
   - The verified archives are published as assets of our own GitHub Release (`media-1`).
   - `media.lock` pins their SHA-256.
   - App workflows download only from there (`fetch_media.py` + `actions/cache`) and fail on a mismatch: no Homebrew,
     apt libmpv or third-party sites.
4. **Licence verification is automatic.** `verify.py` loads the built libraries and fails unless:
   - every FFmpeg library reports `LGPL version 2.1 or later`;
   - none was configured with `--enable-gpl`, `--enable-nonfree` or `--enable-version3`;
   - libmpv reports `-Dgpl=false`;
   - every filter the VLC-parity matrix uses exists, and the GPL-only filters are absent.
5. **Relinking:** every library is shared.
   - macOS: `@rpath` install names with an `@loader_path` rpath; bundled into `Contents/Frameworks`.
   - Linux: `$ORIGIN` rpath; bundled into `lib/`.
   - Windows: DLLs next to `Vimivo.exe`, with the mingw-w64 runtime DLLs they need (winpthreads, GCC runtime).
6. **About shows the notices on all five apps.**
   - The list is generated from the same pinned inputs (`scripts/generate_notices.py` → `ThirdPartyNotices` in
     `shared`), and CI fails when it is stale.
   - About shows the per-platform list, the LGPL notice, and links to the source and the build scripts.
   - The desktop apps ship the full licence texts (`media-licenses/`); Android ships the LGPL 2.1 text.
7. **DVD and Audio CD are deferred** (libdvdnav, libdvdread and libcdio are GPL). Unencrypted Blu-ray stays: libbluray
   is LGPL, with no BD-J and no AACS/BD+.
8. **Conversion (#39)**, if built, uses an optional, separately downloaded encoder module and is never part of the
   default app.

## Desktop media package `media-1`: components and licences

| Component | Version | Licence (as used) | Platforms | Notes |
|---|---|---|---|---|
| mpv (libmpv) | 0.41.0 | LGPL-2.1-or-later | all | `-Dgpl=false`; no Lua/JS, cdda, dvdnav, rubberband |
| FFmpeg | 9.0.2 | LGPL-2.1-or-later | all | `--disable-gpl --disable-nonfree --disable-version3 --disable-programs` |
| libplacebo | 7.360.1 | LGPL-2.1-or-later | all | OpenGL + lcms; Vulkan off |
| Vulkan-Headers (in libplacebo) | sdk-1.4.363.0 | Apache-2.0 OR MIT | all | headers only (Vulkan stubs) |
| fast_float (in libplacebo) | 8.3.2 | Apache-2.0 OR BSL-1.0 OR MIT | all | header-only |
| libass | 0.17.5 | ISC | all | CoreText / DirectWrite / fontconfig |
| FreeType | 2.15.0 | FTL | all | |
| HarfBuzz | 14.6.0 | MIT | all | |
| FriBidi | 1.0.17 | LGPL-2.1-or-later | all | |
| dav1d | 1.5.4 | BSD-2-Clause | all | AV1 |
| libbluray | 1.5.0 | LGPL-2.1-or-later | all | bundles libudfread (LGPL); no BD-J, no DRM bypass |
| uchardet | 0.0.8 | MPL-1.1 / LGPL-2.1-or-later (tri-licence incl. GPL) | all | used under MPL/LGPL |
| Little CMS | 2.19.1 | MIT | all | GPL-3 fast-float/threaded plug-ins **off** |
| zimg | 3.0.6 | WTFPL | all | |
| zlib | 1.3.2 | Zlib | Windows | the system zlib is used elsewhere |
| GNU libiconv | 1.18 | LGPL-2.1-or-later | Windows | library only (the GPL `iconv` program is not shipped); subtitle charsets |
| libdisplay-info | 0.3.0 | MIT (PNP ID table from hwdata under XFree86-1.0) | Linux | needed by mpv's DRM path for VA-API |
| shaderc | 2026.4 | Apache-2.0 | Windows | for mpv's Direct3D 11 renderer |
| glslang (in shaderc) | e1b562a8 | BSD-3/BSD-2/MIT/Apache-2.0; parser GPL-3.0+ **with Bison exception** | Windows | the exception allows any licence |
| SPIRV-Tools, SPIRV-Headers (in shaderc) | ef96ed76, 04fd3caa | Apache-2.0, MIT | Windows | |
| SPIRV-Cross | sdk-1.4.363.0 | Apache-2.0 | Windows | |
| GCC runtime (libgcc, libstdc++) | mingw-w64 toolchain | GPL-3.0+ WITH GCC-exception-3.1 | Windows | linked statically where the build allows; zimg (libtool) needs the DLLs; the exception permits any licence |
| winpthreads | mingw-w64 toolchain | MIT AND BSD-3-Clause | Windows | `libwinpthread-1.dll` |

System libraries are linked but not shipped:

- macOS: system frameworks, `libiconv`, `libz`, the Swift runtime.
- Linux: X11, EGL, VA-API, PulseAudio, ALSA, fontconfig, OpenSSL 3 (Apache-2.0).
- Windows: system DLLs.

Build-only tools are never shipped: pkgconf, GNU m4/autoconf/automake/libtool, nasm, meson, ninja and cmake.

Apache-2.0 code combined with LGPL-2.1-or-later code is distributed under LGPL-3.0 terms where needed. The "or later"
clause allows this for every LGPL component here.

**Platform choices:**

- **Linux has no mpv X11 code:** under `-Dgpl=false`, mpv 0.41 refuses `-Dx11=enabled`, because its X11 code is GPL.
  - Linux therefore renders through the libmpv render API into Vimivo's own EGL context on its X11 window
    (`MpvEglRenderer`, JNA only), as macOS already did.
  - VA-API reaches the GPU through a DRM render node, which needs libdisplay-info.
- **Linux baseline:** the build runs on Ubuntu 22.04 (glibc 2.35). Wayland output and PipeWire are off: XWayland
  covers Wayland sessions, and PipeWire is reached through PulseAudio.
- **macOS:** deployment target 11.0, universal arm64 + x86_64.

## Behaviour changes with the new binaries

- **Linux video output changed:** libmpv's own X11 `vo=gpu` embedding via `wid` → `vo=libmpv` rendered by
  `MpvEglRenderer`. The smoke test checks the picture with the same pixel probes. The X error-handler workaround for
  libmpv is gone, because libmpv no longer touches Xlib.
- **Windows** additionally ships `libiconv-2.dll` and the mingw-w64 runtime DLLs (`libwinpthread-1.dll`,
  `libgcc_s_seh-1.dll`, `libstdc++-6.dll`), replacing the single monolithic GPL `libmpv-2.dll`.
- **Library video metadata and thumbnails** now come from the bundled libmpv (`MpvMediaInspector`). They used to come
  from external `ffprobe`/`ffmpeg` programs, which the apps never shipped, so on machines without ffmpeg they silently
  failed; CI hid that by installing ffmpeg. Media Info uses the same inspector (header sniffing only for files libmpv
  cannot open). Neither the apps nor CI run an ffmpeg/ffprobe program any more.
- **No user-visible regressions** found in the smoke tests (see the CI table in the (d) report).
  - Hardware decoding stays on: VideoToolbox (zero-copy, `videotoolbox-gl`) on macOS, D3D11VA/DXVA2 on Windows,
    VA-API on Linux.
  - Timeline previews (`vo=image`, JPEG) work without libjpeg; libavcodec's MJPEG encoder writes them.
- Formats that needed GPL code are gone:
  - x264/x265 **encoding**: never used. Decoding of H.264/HEVC uses FFmpeg's native decoders and is unaffected.
  - libpostproc: unused.
  - DVD/CD: deferred, see Decision 7.
- GPL-only filters are unavailable. Replacements are listed in `docs/VLC_FEATURE_GAP.md` ("LGPL filter availability").
- TLS:
  - macOS: SecureTransport (deprecated by Apple, still functional).
  - Windows: SChannel.
  - Linux: the system OpenSSL 3.

## Mobile

See `docs/LICENSE_AUDIT_MOBILE.md`.

- **Android:** LibVLC 3.7.7 from Maven contains GPL modules (x264 encoder, dvdnav/dvdread, postproc).
  - The app is a GPL combination today.
  - Recommended fix: our own LibVLC build without those modules, pinned and verified like the desktop package.
  - Until then, no public Android build ships with LibVLC.
- **iOS:**
  - AVPlayer only: system frameworks, no third-party media code.
  - MPVKit is unusable for the fallback: static xcframeworks, and gnutls/gmp/nettle (LGPLv3) even in its LGPL product.
  - The fallback will be our own dynamic frameworks from `scripts/build_media`.

## Implementation status (2026-10-20)

| Item | Status |
|---|---|
| Build scripts, manifest, verification (`scripts/build_media`) | Done |
| CI build + per-OS verification (`build-media.yml`) | Done |
| Release `media-1` + `media.lock`; app workflows fetch from it | Done |
| Homebrew / apt libmpv / third-party downloads removed from CI | Done (macOS, Windows, Linux, release candidate) |
| macOS Intel smoke test re-enabled | Done |
| About notices on all five apps | Done |
| Filter check (#12–15, #19–21, #24) | Done |
| LibVLC LGPL-only build (Android) | Open: product decision |
| iOS dynamic LGPL frameworks | Open |
