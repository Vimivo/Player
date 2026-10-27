# Dependency and native-library license audit

> **Superseded for native media libraries (2026-10-26):** LibVLC, the "material licensing risk" below, was removed. Android and the desktop apps now use Vimivo's own LGPL-only libmpv/FFmpeg build with a reproducible pipeline and automatic licence verification: see [ADR-004](adr/ADR-004-LGPL-MEDIA-STACK.md), [ADR-005](adr/ADR-005-MOBILE-MEDIA-ENGINE.md) and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md). The LibVLC findings are kept for history.

Audit date: 2026-10-13. Versions are those pinned in `gradle/libs.versions.toml` and resolved by the release project. This engineering audit is not legal advice.

| Dependency group | Version | License | Release obligations |
|---|---:|---|---|
| Android Gradle Plugin / AndroidX / Media3 / Room / Compose UI test | pinned catalog versions | Apache License 2.0 | Preserve notices and license text; state modifications where required. |
| Kotlin / Compose Multiplatform / kotlinx.coroutines / serialization | 2.3.20 / 1.9.3 / 1.10.2 / 1.9.0 | Apache License 2.0 | Preserve copyright, notice, and license text. |
| Koin | 4.2.2 | Apache License 2.0 | Preserve copyright, notice, and license text. |
| Detekt | 1.23.8 | Apache License 2.0 | Build-time only; retain repository attribution. |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 | Test-only; not packaged in the release runtime. |
| LibVLC Android (`libvlc-all`) | 3.7.7 | GNU LGPL 2.1 (published POM) | Include LGPL text and notices; allow replacement/relinking of the LGPL component; provide Corresponding Source, including applicable build scripts and modifications, by a compliant method. Preserve third-party codec notices. |
| Jaudiotagger (`net.jthink:jaudiotagger`) | 3.0.1 | LGPL (published POM; project source headers describe LGPL 2.1-or-later) | Bundle the applicable LGPL text and attribution, keep the library replaceable as a distinct JAR, and publish modifications to the library if Vimivo makes any. Vimivo currently consumes it unmodified. |

## Native contents

The debug APK inspection found `libvlc.so`, `libvlcjni.so`, `libc++_shared.so`, AndroidX graphics, and DataStore native libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`; no x86 binary was packaged. The same audit must be repeated on the final AAB because Play splits delivery by ABI.

LibVLC is the material licensing risk. Its Maven POM declares LGPL 2.1 and links to the VideoLAN source project. Before release:

1. Archive the exact LibVLC 3.7.7 source and build inputs corresponding to the shipped binary.
2. Publish a durable source/download page or make a legally reviewed written offer, as applicable.
3. Provide installation/relinking information sufficient for recipients to replace the LGPL component in the packaged app; R8 rules must not frustrate that right.
4. Include the full LGPL 2.1 license and all notices from the AAR/source distribution in the app or an easily accessible accompanying location.
5. Review every codec/plugin included by the selected LibVLC artifact for patent, export, and regional distribution constraints.

The [VideoLAN legal page](https://www.videolan.org/legal.html), the artifact's published POM, and the corresponding source distribution are authoritative inputs. Do not infer that VLC application GPL terms apply to Vimivo merely because it uses LibVLC, and do not claim compliance until the replacement/source-delivery process is tested.

## macOS Desktop Native Contents and Licenses

Audit date: 2026-10-15 for arm64 macOS standalone distribution (`Vimivo.app` and `Vimivo-1.0.0.dmg`).

| Native Component | Version | License | Redistribution Notes |
|---|---:|---|---|
| libmpv | 0.41.0 | LGPL 2.1+ / GPL 2+ | Dynamically linked via JNA and Cocoa render bridge. Bundled in `Contents/Frameworks/`. |
| FFmpeg (avcodec, avformat, avfilter, avutil, swresample, swscale, avdevice) | 9.0.2 | LGPL 2.1+ / GPL 2+ | Transitive dependency of libmpv. Bundled with `@rpath` install names. |
| libass | 0.17.5 | ISC License | Subtitle renderer. |
| libplacebo | 7.360.1 | LGPL 2.1+ | GPU image processing and color management. |
| dav1d | 1.5.4 | BSD 2-Clause | AV1 decoder. |
| fribidi | 1.0.17 | LGPL 2.1+ | Bi-directional text support. |
| harfbuzz | 14.6.0 | MIT (Old Style) | Text shaping engine. |
| freetype | 2.14.3 | FreeType License / GPLv2 | Font rendering. |
| libvpx | 1.17.0 | BSD 3-Clause | VP8/VP9 video codec. |
| opus | 1.6.1 | BSD 3-Clause | Opus audio codec. |
| libpng | 1.6.59 | PNG License | PNG image decoding. |
| libjpeg-turbo | 3.2.0 | BSD-style / IJG | JPEG image decoding. |
| zimg | 3.0.6 | WTFPL / Public Domain | Scaling and colorspace conversion. |
| uchardet | 0.0.8 | MPL 1.1 / LGPL 2.1+ | Subtitle encoding detection. |
| luajit | 2.1 | MIT License | mpv scripting engine. |
| mujs | 1.3.10 | ISC License | mpv Javascript engine. |
| little-cms2 | 2.19.1 | MIT License | ICC color management. |
| libarchive | 3.8.9 | BSD 2-Clause | Archive format support. |
| librubberband | 4.0.0 | GPL 2+ | Audio pitch and tempo scaling. |
| JNA (`net.java.dev.jna:jna`) | 5.14.0 | Apache 2.0 / LGPL 2.1 | Native binding layer for libmpv. |
| SQLite JDBC | 3.45.1.0 | Apache 2.0 / Public Domain | Bundled JDBC native SQLite engine. |

### GPL Compliance Notice for Desktop Distribution

Because the current Homebrew-built `libmpv` and `ffmpeg` link GPL components (such as `librubberband`, `x264`, and `x265`), the distributed desktop application image is subject to GNU GPL v2/v3 source disclosure terms for bundled binaries. Complete source code for Vimivo is maintained at https://github.com/Vimivo/Player. For a pure LGPL desktop distribution, a custom build of libmpv configured with `--enable-lgpl` and without GPL-only filters/codecs should be compiled.

## Compatibility conclusion

No known license in the declared application runtime is inherently incompatible with a free open-source distribution. **Release remains blocked on Apple Developer ID code signing and Apple notarization.** For public DMG distribution, binary tickets must be stapled after `xcrun notarytool` acceptance.

