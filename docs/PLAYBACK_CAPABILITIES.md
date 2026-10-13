# Playback capabilities

This document describes the implementation, not a promise that every codec/container combination works on every device. Container parsing, codec decoding, profile/level, resolution, bit depth, DRM, and hardware all affect playback.

## Engine comparison

| Capability | Media3 1.11.1 | LibVLC 3.7.7 |
|---|---|---|
| Role | Primary | Compatibility fallback |
| Local `content://` / `file://` | Implemented | Implemented |
| HTTP/HTTPS | Implemented | Implemented |
| Hardware video decoding | Android `MediaCodec`, device-dependent | Enabled when available |
| Software fallback | No optional Media3 decoder extensions bundled | Enabled by LibVLC |
| Play/pause/stop/resume/seek | Implemented | Implemented |
| Speed and volume | Implemented | Implemented |
| Position/duration/buffering | Implemented | Position/duration implemented; byte/time buffered position is not exposed by LibVLC and is reported conservatively |
| Audio/video/subtitle tracks | Implemented | Implemented; detailed metadata depends on LibVLC discovery |
| Embedded subtitles | Implemented when container/extractor supports them | Implemented |
| External SRT, ASS/SSA, WebVTT | MediaItem subtitle configurations; rendering is cue-based and complex ASS behavior is not guaranteed | Native LibVLC subtitle slave; complex ASS styling is preserved when supported by the bundled renderer |
| Subtitle appearance/position | PlayerView renderer supports size, color, background, outline/shadow, and bottom padding | Not exposed because LibVLC 3.7.7 has no equivalent stable Android API |
| Subtitle/audio delay | Not exposed by the Media3 player API | Native millisecond UI mapped to LibVLC microsecond delay APIs |
| HDR | Extraction and decoder selection supported; display/device-dependent | Decoder/output/device-dependent; must be device tested |
| Frame rate and video size | Reported when Media3 `Format` supplies them | Reported for the selected parsed video track |
| Aspect ratio | `PlayerView` currently uses its default fit behavior | `VLCVideoLayout` default behavior; user controls not yet exposed |
| Audio focus | Media3-managed | Explicit Android `AudioFocusRequest` |
| Audio-becoming-noisy | Media3-managed | Not yet wired for LibVLC |

Media3 automatically uses the Android platform decoder selected for the stream profile. Optional Media3 software decoder extensions are not bundled, so AV1, VP9, HEVC, ALAC, WMA, and other codec support can vary by Android version and device. LibVLC is the fallback for additional demuxer/decoder coverage, but compatibility is still not absolute.

## Format targets

| Family | Current routing and caveats |
|---|---|
| MP4, M4A, MOV, 3GP | Media3 first. Codec/profile must be supported by the device. |
| MKV/Matroska, WebM | Media3 first. Multiple tracks and embedded subtitles are surfaced when recognized. |
| MPEG-TS, M2TS, MPEG-PS | Media3 first; seeking depends on stream/index characteristics. |
| FLV | Media3 first; Media3 documents progressive FLV as non-seekable. |
| AVI | Routed to LibVLC because Media3 does not document an AVI progressive extractor. |
| HEIF/HEIC | Media3 supports HEIF/HEIC images, not arbitrary HEVC video merely because it uses a `.heif` extension. Not advertised as a video container. |
| MP3, AAC/ADTS, FLAC, WAV, Ogg/Vorbis, Opus | Media3 first. Seeking limitations can apply to streams without an index. |
| ALAC, WMA | LibVLC fallback unless the actual source/container is recognized by Media3/device. |
| H.264, H.265/HEVC, VP9, AV1, MPEG-2, MPEG-4 | Decoder availability, profile, level, bit depth, and resolution are device-specific for Media3. LibVLC software fallback is attempted only after a verified decoder/decode failure. |

Unknown HTTP(S) paths are offered to Media3 because servers commonly omit file extensions and the extractor can sniff content. Authentication, access-control, and DRM failures never trigger LibVLC fallback. Vimivo does not bypass DRM.

## Subtitle and track policy

Embedded audio and subtitle tracks are normalized into the common `TrackInfo` model. External subtitle URIs are selected with the Storage Access Framework, validated as readable SRT, ASS/SSA, or WebVTT text, and retained only through Android's persisted read grant. Automatic matching searches only folders the user has explicitly granted and recognizes `movie.srt` and language-suffixed names such as `movie.en.srt`.

Media3 cues are forwarded through the session adapter to `PlayerView`. Supported embedded spans are retained, but Media3 is not documented as a complete libass renderer, so full ASS/SSA fidelity is not claimed. LibVLC draws its subtitles natively on the video surface. Appearance controls are consequently enabled only for Media3, while audio/subtitle delay controls are enabled only for LibVLC.

## Audio investigation

- Gapless playback: Media3 supports playlist transitions and encoder delay/padding metadata for suitable sources, but Vimivo's manager does not yet auto-advance its shared queue. Gapless is therefore not claimed.
- ReplayGain: neither adapter currently applies ReplayGain metadata or DSP; not implemented.
- Audio effects: no equalizer, loudness enhancer, or LibVLC equalizer is enabled. Effects require a separate lifecycle and output-session design.
- Output: Media3 uses default media audio attributes and device routing. LibVLC uses its default output with Android audio focus. Passthrough/offload controls are not exposed.

## Native compatibility and licenses

The Maven Central `org.videolan.android:libvlc-all:3.7.7` POM declares **GNU LGPL version 2.1**. The inspected AAR contains `arm64-v8a`, `armeabi-v7a`, x86, and `x86_64`; Vimivo packages all except obsolete x86. ARM64 ELF load segments were inspected as `2**14`, and the debug APK passed Android build-tools `zipalign -c -P 16 -v 4`.

Media3 is part of AndroidX and is licensed under Apache License 2.0. See [third-party notices](THIRD_PARTY_NOTICES.md). Shipping builds must retain the applicable notices and LGPL compliance materials.

