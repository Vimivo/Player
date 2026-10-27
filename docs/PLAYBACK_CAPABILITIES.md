# Playback capabilities

This document describes the implementation, not a promise that every codec/container combination works on every device. Container parsing, codec decoding, profile/level, resolution, bit depth, DRM, and hardware all affect playback.

## Engine comparison

| Capability | Media3 1.11.1 | libmpv 0.41.0 (Vimivo's LGPL build, ADR-005) |
|---|---|---|
| Role | Primary | Compatibility fallback (replaced LibVLC) |
| Local `content://` / `file://` | Implemented | Implemented (`content://` through a ContentResolver descriptor) |
| HTTP/HTTPS | Implemented | Plain HTTP and RTSP only; HTTPS stays with Media3 until the package has TLS |
| Hardware video decoding | Android `MediaCodec`, device-dependent | Explicit "hardware" decoder choice only (`mediacodec-copy`); Automatic decodes in software, because FFmpeg's MediaCodec path stalled on the emulator's decoder (real devices: manual checklist) |
| Software fallback | No optional Media3 decoder extensions bundled | FFmpeg 9.0.2 decoders (including dav1d for AV1) |
| Play/pause/stop/resume/seek | Implemented | Implemented (exact or keyframe seeking) |
| Speed and volume | Implemented | Implemented |
| Position/duration/buffering | Implemented | Implemented, including demuxer cache time |
| Audio/video/subtitle tracks | Implemented | Implemented; codec, bitrate, size, frame rate and language per track |
| Embedded subtitles | Implemented when container/extractor supports them | Implemented (libass renders ASS/SSA) |
| External SRT, ASS/SSA, WebVTT | MediaItem subtitle configurations; rendering is cue-based and complex ASS behavior is not guaranteed | `sub-add` with libass; styling preserved |
| Subtitle appearance/position | PlayerView renderer supports size, color, background, outline/shadow, and bottom padding | Not wired yet (libmpv exposes `sub-*` properties; V1 item) |
| Subtitle/audio delay | Not exposed by the Media3 player API | Implemented (`sub-delay`, `audio-delay`) |
| HDR | Extraction and decoder selection supported; display/device-dependent | Tone-mapped by libmpv's GPU renderer; device tested only on the emulator |
| Frame rate and video size | Reported when Media3 `Format` supplies them | Reported per track |
| Aspect ratio | `PlayerView` currently uses its default fit behavior | Fit (letterboxed); user controls are a V1 item |
| Audio focus | Media3-managed | Explicit Android `AudioFocusRequest` (pause, duck, resume) |
| Audio-becoming-noisy | Media3-managed | Not yet wired |

Media3 automatically uses the Android platform decoder selected for the stream profile. Optional Media3 software decoder extensions are not bundled, so AV1, VP9, HEVC, ALAC, WMA, and other codec support can vary by Android version and device. libmpv is the fallback for unsupported containers and decoder failures; see [network streaming](NETWORK_STREAMING.md) for the bounded policy.

## Format targets

| Family | Current routing and caveats |
|---|---|
| MP4, M4A, MOV, 3GP | Media3 first. Codec/profile must be supported by the device. |
| MKV/Matroska, WebM | Media3 first. Multiple tracks and embedded subtitles are surfaced when recognized. |
| MPEG-TS, M2TS, MPEG-PS | Media3 first; seeking depends on stream/index characteristics. |
| FLV | Media3 first; Media3 documents progressive FLV as non-seekable. |
| AVI | Routed to libmpv because Media3 does not document an AVI progressive extractor. |
| HEIF/HEIC | Media3 supports HEIF/HEIC images, not arbitrary HEVC video merely because it uses a `.heif` extension. Not advertised as a video container. |
| MP3, AAC/ADTS, FLAC, WAV, Ogg/Vorbis, Opus | Media3 first. Seeking limitations can apply to streams without an index. |
| ALAC, WMA | libmpv fallback unless the actual source/container is recognized by Media3/device. |
| H.264, H.265/HEVC, VP9, AV1, MPEG-2, MPEG-4 | Decoder availability, profile, level, bit depth, and resolution are device-specific for Media3. libmpv software fallback is attempted only after a verified decoder/decode failure. |

Unknown HTTP(S) paths are offered to Media3 because servers commonly omit file extensions and the extractor can sniff content. Authentication, access-control, and DRM failures never trigger libmpv fallback. Vimivo does not bypass DRM.

## Subtitle and track policy

Embedded audio and subtitle tracks are normalized into the common `TrackInfo` model. External subtitle URIs are selected with the Storage Access Framework, validated as readable SRT, ASS/SSA, or WebVTT text, and retained only through Android's persisted read grant. Automatic matching searches only folders the user has explicitly granted and recognizes `movie.srt` and language-suffixed names such as `movie.en.srt`.

Media3 cues are forwarded through the session adapter to `PlayerView`. Supported embedded spans are retained, but Media3 is not documented as a complete libass renderer, so full ASS/SSA fidelity is not claimed. libmpv draws its subtitles natively on the video surface with libass. Appearance controls are consequently enabled only for Media3, while audio/subtitle delay controls are enabled only for libmpv.

## Audio investigation

- Gapless playback: Media3 supports playlist transitions and encoder delay/padding metadata for suitable sources, but Vimivo's manager does not yet auto-advance its shared queue. Gapless is therefore not claimed.
- ReplayGain: neither adapter currently applies ReplayGain metadata or DSP; not implemented.
- Audio effects: no equalizer or loudness enhancer is enabled on either Android engine (libmpv's filter chain is a V1 item). Effects require a separate lifecycle and output-session design.
- Output: Media3 uses default media audio attributes and device routing. libmpv uses AAudio (AudioTrack fallback) with Android audio focus. Passthrough/offload controls are not exposed.

## Native compatibility and licenses

The libmpv fallback is Vimivo's own LGPL-only build (`scripts/build_media`, release `media-2`): libmpv 0.41.0 with `-Dgpl=false`, FFmpeg 9.0.2 without GPL, version-3 or non-free parts, for `arm64-v8a`, `armeabi-v7a` and `x86_64`, built with NDK r27 and 16 KB page alignment. Every library is a plain `lib*.so` inside the APK, so a user can replace it. See [ADR-005](adr/ADR-005-MOBILE-MEDIA-ENGINE.md) and [ADR-004](adr/ADR-004-LGPL-MEDIA-STACK.md). LibVLC, which contained GPL modules ([audit](LICENSE_AUDIT_MOBILE.md)), is no longer shipped.

Media3 is part of AndroidX and is licensed under Apache License 2.0. See [third-party notices](THIRD_PARTY_NOTICES.md). Shipping builds must retain the applicable notices and LGPL compliance materials.

