# Vimivo Playback Compatibility & Codec Issue Reporting

## 1. Overview

Multimedia playback spans thousands of container formats, video codecs, audio profiles, subtitle tracks, and platform-specific hardware decoders. When playback fails, stutters, drops frames, or exhibits audio/video desync, structured technical reporting is critical to diagnosing root causes.

---

## 2. Safe Media Playback Context Model

When reporting a playback or codec defect, Vimivo captures an explicit `MediaPlaybackContext` structure:

| Field | Description | Example Values |
| :--- | :--- | :--- |
| `container` | File container format | `MKV`, `MP4`, `FLAC`, `WebM`, `OGG`, `WAV` |
| `audioCodec` | Audio codec identifier | `flac`, `aac`, `opus`, `mp3`, `alac`, `vorbis` |
| `videoCodec` | Video codec identifier | `h264`, `hevc` (H.265), `vp9`, `av01` (AV1) |
| `resolution` | Video dimensions | `1920x1080`, `3840x2160` (4K UHD) |
| `frameRate` | Video frame rate | `23.976 fps`, `29.97 fps`, `60.0 fps` |
| `subtitleFormat` | Subtitle track format | `SubRip (SRT)`, `Advanced SubStation Alpha (ASS)`, `WebVTT` |
| `hardwareAcceleration` | Decoder acceleration mode | `auto-safe (VideoToolbox / D3D11VA / VAAPI)`, `software` |
| `playbackEngine` | Active engine abstraction | `MpvPlayerEngine` (Desktop), `Media3PlayerEngine` (Android) |
| `errorMessage` | Sanitized engine error code | `MPV_ERROR_UNSUPPORTED_FORMAT`, `ERROR_CODE_DECODER_INIT_FAILED` |

### Excluded Data Guarantee
Vimivo **NEVER** includes:
- File paths or filesystem directory trees (e.g., `/Users/username/Movies/private_video.mkv` is completely omitted).
- File size or file hash checksums of personal media.
- Embedded ID3 / Vorbis personal metadata (artist, title, comments).
- Embedded album artwork or video thumbnails.
- Network stream credentials or authentication headers.

---

## 3. Playback Issue Categories

1. **Hardware Decoder Failure**: Video freezes while audio plays, or video playback crashes on 10-bit HDR content.
2. **Audio/Video Desynchronization**: Audio drifts ahead of or behind video over time, or after seeking.
3. **Subtitle Synchronization & Rendering**: Complex ASS styling causes frame drops, or subtitles disappear during seek.
4. **Seeking & Buffer Starvation**: Seeking takes >2 seconds or produces an infinite loading spinner.
5. **Stream Reconnection & Network Drops**: Direct HTTP/HLS streams fail to resume when network connectivity fluctuates.

---

## 4. How to Provide Safe Reproduction Media

To test playback bugs without violating copyright or sharing private media:
1. **Public Domain Test Clips**: Use standardized test clips from Blender Foundation (Big Buck Bunny, Tears of Steel, Sintel) or Xiph.org media collections.
2. **Synthetic FFmpeg Test Streams**:
   - Generate test video with timecode:
     ```bash
     ffmpeg -f lavfi -i testsrc=duration=10:size=1920x1080:rate=60 -c:v libx264 -pix_fmt yuv420p test_h264.mp4
     ```
   - Generate tone audio:
     ```bash
     ffmpeg -f lavfi -i sine=frequency=440:duration=5 -c:a flac test_tone.flac
     ```
