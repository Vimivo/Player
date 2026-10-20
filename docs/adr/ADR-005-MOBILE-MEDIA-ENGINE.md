# ADR-005: Mobile media engine (Android fallback, iOS fallback)

Status: **Accepted** (2026-10-20): Android option (a), role (i) first; armeabi-v7a kept; iOS plan as below. Date: 2026-10-20. Builds on ADR-004 and
`docs/LICENSE_AUDIT_MOBILE.md`.

## Context

- **Android:** Media3 is the preferred engine. LibVLC 3.7.7 from Maven is the fallback for formats Media3 cannot play;
  `PlaybackEngineManager` picks per item by `supportFor()`.
  - That LibVLC contains GPL code (x264 encoder, dvdnav/dvdread, postproc), so no public Android build can ship with
    it.
- **iOS:** AVPlayer only. The decided fallback, MPVKit, is unusable: static xcframeworks, and LGPLv3/GPL math libraries
  even in its "LGPL" product.
- **Desktop:** ADR-004 gave the desktop apps one LGPL-only libmpv + FFmpeg stack, built from pinned sources, verified,
  published in our Releases and consumed through `media.lock`.
- **What a shared engine buys:** one engine across desktop, Android and the iOS fallback gives one filter set (EQ,
  deinterlacing, picture adjustments, audio effects; see the matrix's LGPL filter table), one subtitle renderer
  (libass) and one set of codec behaviours.

## Options for Android

| | (a) Our own LGPL libmpv on Android | (b) Our own LibVLC without GPL modules |
|---|---|---|
| Engine and filters | Same as desktop: libmpv + lavfi filters, libass, libplacebo. Filter, EQ and subtitle code shared with desktop. | VLC modules: a second filter implementation, different EQ bands and subtitle renderer. |
| Build pipeline | `scripts/build_media` gains an `android` target (NDK, meson/configure cross-files like the mingw one). Same manifest, `verify.py` checks, Release and `media.lock`. | VLC-Android's `compile-libvlc.sh` plus about 100 VLC contribs; a separate pipeline. Its sources sit behind code.videolan.org's anti-bot page (fetch failed in the audit), so mirrors are needed. |
| GPL exclusion | `-Dgpl=false` is an upstream switch; FFmpeg `--disable-gpl`. Verified by the existing `verify.py`. | Per-module: x264, dvdnav/dvdread, postproc and sout must be removed, and every VLC upgrade re-audited. No single switch. |
| Android integration | MediaCodec hardware decoding (`hwdec=mediacodec`, zero-copy via `vo=mediacodec_embed` or `gpu` + `gpu-context=android`). Audio through AAudio, AudioTrack fallback. Video into a `Surface` (`wid`). All LGPL in mpv 0.41. | Mature: VLC for Android's own choices. |
| App code | New `LibmpvPlayerEngine` (Kotlin) behind the existing `PlayerEngine` contract, plus a small JNI shim (ours, MIT) for `JNI_OnLoad`/`av_jni_set_java_vm` and the `Surface`. `SessionPlayerAdapter` keeps Media3's system session. | Keep `LibVlcPlayerEngine` as is. |
| Size per ABI (uncompressed, estimate) | About 35 MB (desktop arm64 slice of the same stack: about 36 MB). | Today 45.9 MB `libvlc.so` + 9.3 MB `libc++_shared.so` on arm64-v8a; similar without GPL modules. |
| Build cost in CI | 3 ABIs × about 40–60 min (desktop targets take 1–2 h on hosted runners). | 3 ABIs × about 1–2 h (VLC contribs). |
| Risks | New Android engine code: surface lifecycle, audio focus via the existing adapter, MediaCodec quirks. mpv-android shows the platform works, but we write our own integration. | Two engines with diverging features forever; contrib fetching; a large audit surface per release. |

## Recommendation for Android: (a)

The reasons are your stated goal (one engine and one filter set) and the pipeline that already exists:

- the GPL exclusion is a single upstream switch with automatic verification;
- the desktop packaging, release and checksum flow is reused unchanged;
- the matrix's filter work (#12–15, #19–24) is written once.

**Phased plan (after your OK):**

1. **Build.**
   - Add an `android` target to `scripts/build_media` (NDK r27 LTS, API 26) for arm64-v8a, armeabi-v7a and x86_64.
   - FFmpeg: `--target-os=android --enable-jni --enable-mediacodec`, still `--disable-gpl --disable-version3`.
   - mpv: `-Dandroid-media-ndk` where available, `egl-android`, `aaudio`, `audiotrack`, `opensles=disabled`.
   - Run `verify.py` on an x86_64 emulator in CI.
   - Release `media-2` with the AAR-ready `jniLibs/<abi>/` trees plus `media.lock` entries.
2. **Engine.**
   - `LibmpvPlayerEngine` + JNI shim in `androidApp`, registered as a factory with FALLBACK support, exactly where
     LibVLC is today.
   - Capabilities and failure reporting per AGENTS.md.
   - Tests: the engine contract tests, the existing emulator smoke test, the Open-with flow.
3. **Switch.**
   - Remove LibVLC (dependency, `LibVlcPlayerEngine`, notices).
   - Regenerate the third-party list.
   - Update ADR-004/005 and the matrix rows that cite LibVLC.
4. **Later, a separate decision:** route video to libmpv by default when the user enables a filter Media3 cannot do.
   Media3 stays preferred for plain playback: battery, gapless, ReplayGain and system integration are already tested
   there.

**Open questions for you:**

- **Role:** (i) fallback only, replacing LibVLC 1:1 (recommended first), or (ii) libmpv as the primary video engine
  now.
- **armeabi-v7a:** keep it? minSdk 26 still includes 32-bit ARM devices. Dropping it saves about a third of the build
  time and APK splits.

## iOS fallback: our own dynamic LGPL frameworks

Same pipeline, `ios` target, no third-party binaries:

- **Slices:**
  - `ios-arm64` (devices, iOS 16+);
  - `ios-arm64_x86_64-simulator` (lipo; x86_64 for Intel Macs running the simulator).
- **Frameworks:**
  - One `.framework` per library (`Libmpv`, `Libavcodec`, `Libavformat`, `Libavutil`, `Libavfilter`, `Libswscale`,
    `Libswresample`, `Libass`, `Libplacebo`, `Libfreetype`, `Libharfbuzz`, `Libfribidi`, `Libdav1d`, `Libuchardet`),
    packaged as xcframeworks.
  - Each has an `Info.plist` (CFBundle keys, `MinimumOSVersion`), an `@rpath/<Name>.framework/<Name>` install name,
    and a `PrivacyInfo.xcprivacy` declaring the required-reason APIs they use (file timestamps, system boot time) and
    no tracking.
  - Embedded and signed by Xcode; App Store compatible: dynamic frameworks are allowed since iOS 8.
- **Licences: LGPL-2.1-or-later only, no LGPLv3 dependencies.**
  - FFmpeg `--disable-gpl --disable-version3 --disable-nonfree`, mpv `-Dgpl=false`.
  - No gnutls, gmp or nettle: TLS through FFmpeg's `securetransport` (system), or network sources stay on AVPlayer.
  - No Vulkan/MoltenVK (Apache-2.0 and a large extra surface).
  - No libbluray or lcms2 on iOS (not needed).
- **Rendering and decoding:**
  - libmpv render API with OpenGL ES 3 (`ios-gl`, VideoToolbox zero-copy interop) into Vimivo's own layer, the same
    model as macOS. Apple deprecated OpenGL ES, but it is available on iOS 16–26; a Metal path can follow later
    through libplacebo.
  - Audio through `ao=audiounit`. The `AVAudioSession` stays owned by the app.
- **Relinking:** the libraries are separate dynamic frameworks, and Vimivo's own source is public (MIT), so a user can
  rebuild the app with a modified library. That is the LGPL-2.1 §6 path; LGPLv3's installation-information clause,
  which conflicts with App Store signing, never applies.
- **Integration:**
  - A Swift `MpvFallbackPlayer` in `iosApp`, chosen when AVPlayer reports an unsupported format.
  - Shared queue, resume and preferences come from `VimivoShared` (the iOS persistence migration to `shared` comes
    first in V1).
  - `verify.py` runs on the simulator slice in `ios.yml`.

## Decision

Accepted on 2026-10-20:

- **Android (a):** our own LGPL libmpv. It replaces LibVLC 1:1 as the fallback engine; Media3 stays preferred.
- **ABIs:** arm64-v8a, armeabi-v7a and x86_64 (the ABIs requested; armeabi-v7a kept).
- **iOS:** dynamic LGPL-2.1-or-later frameworks as above, after the iOS persistence migration.
