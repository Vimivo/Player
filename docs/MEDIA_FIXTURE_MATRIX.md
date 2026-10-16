# Vimivo — Test Media Fixture Matrix & Provenance Specification

> [!NOTE]
> Authoritative test fixture catalog and licensing provenance specification documented under **Milestone 45 (Real Media Compatibility)**.
> Enforces 100% legal, permissively licensed (CC0 / MIT / Public Domain) or programmatically generated test vectors. Zero proprietary or copyrighted commercial media is stored in Git history.

---

## 1. Compliance & Security Rules for Fixtures

1. **No Commercial Media**: Never commit proprietary songs, movie clips, or licensed TV broadcast streams.
2. **Deterministic Generation**: All standard media fixtures must be reproducible using open-source tools (e.g. FFmpeg `lavfi` synthetic filters) or programmatic byte generation.
3. **Bounded Size**: In-tree test fixtures must be lightweight (< 256 KB) to prevent Git repository bloat. Large 4K/HDR verification corpuses reside in external test caches or CI artifact stores.
4. **Negative Test Vectors**: Explicitly include malformed, truncated, and corrupt headers to verify engine security and exception safety.

---

## 2. Programmatic Test Fixture Matrix

| Fixture ID | Category | Container | Codec / Content | Parameters | Provenance / Generator | Test Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `FIX-AUD-FLAC-01` | Lossless Audio | `.flac` | FLAC 16-bit 44.1 kHz | Stereo, 1.0s sine wave | `fLaC` header + streaminfo generator | Magic sniffing & lossless playback |
| `FIX-AUD-OGG-01` | Compressed Audio| `.ogg` | Ogg Vorbis | Stereo, 48 kHz | `OggS` container generator | Container demuxing & metadata |
| `FIX-AUD-WAV-01` | Uncompressed Audio| `.wav` | LPCM 16-bit 44.1 kHz | Stereo, 1.0s silence | `RIFF....WAVE` chunk generator | Bit-perfect PCM audio output |
| `FIX-AUD-MP3-01` | Compressed Audio| `.mp3` | MP3 320 kbps CBR | Stereo, 44.1 kHz | ID3v2.4 + MP3 frame generator | ID3 tag parsing & seek timing |
| `FIX-AUD-OPUS-01` | Low Latency Audio| `.opus` | Ogg Opus 48 kHz | Stereo, 1.0s synthetic | FFmpeg `lavfi` sine wave | Variable bitrate audio decoding |
| `FIX-VID-AVC-01` | HD Video | `.mp4` | H.264 High Profile | 1080p30, 8-bit, 2.0s | FFmpeg `testsrc2` + `libx264` | GPU VideoToolbox / D3D11VA decoding |
| `FIX-VID-HEVC-01` | 4K UHD Video | `.mp4` | H.265 Main 10 Profile | 2160p30, 10-bit HDR10 | FFmpeg `testsrc2` + `libx265` | 10-bit HDR decoding & tone mapping |
| `FIX-VID-MKV-01` | Multi-Track Video| `.mkv` | H.264 + Dual AAC | Dual audio + dual subtitles | Matroska muxer | Multi-track audio/subtitle switching |
| `FIX-VID-WEBM-01` | Web Video | `.webm`| VP9 Profile 0 + Opus | 720p60, 8-bit SDR | FFmpeg `libvpx-vp9` | WebM container demuxing & playback |
| `FIX-SUB-SRT-01` | Subtitles | `.srt` | UTF-8 Plain Text | 5 timed subtitle cues | Text editor (CC0) | Subtitle timing & delay offset |
| `FIX-SUB-ASS-01` | Styled Subtitles | `.ass` | Advanced SubStation Alpha | Vector styles, fonts, margins | `libass` compliance generator | Complex vector styling & positioning |
| `FIX-SUB-VTT-01` | Web Subtitles | `.vtt` | WebVTT cues | Timed cue blocks | Text editor (CC0) | WebVTT cue parsing & display |

---

## 3. Negative & Security Test Fixtures

| Fixture ID | Category | Test Condition | Expected Safe Engine Behavior | Automated Test Suite |
| :--- | :--- | :--- | :--- | :--- |
| `FIX-NEG-EMPTY-01` | Negative | Zero-byte empty file (`0 bytes`) | Throws `VimivoError` / returns `null` probe details without crash | `MediaCompatibilityTest.testProbeEmptyFileReturnsNull()` |
| `FIX-NEG-TRUNC-01` | Negative | Truncated header (`< 4 bytes`) | Returns `null` without throwing `IndexOutOfBoundsException` | `MediaCompatibilityTest.testProbeTruncatedHeaderReturnsNull()` |
| `FIX-NEG-MISS-01` | Negative | File path does not exist on disk | Emits `FileNotFoundException` / structured I/O failure | `MediaCompatibilityTest.testProbeNonExistentFileReturnsNull()` |
| `FIX-NEG-CORR-01` | Negative | Valid container with corrupt audio frames | Decodes available frames, gracefully handles EOF or decode error | `FaultInjectionReliabilityTest` |
| `FIX-NEG-CODEC-01` | Negative | Unsupported proprietary video codec | Emits `UNSUPPORTED_CODEC` error code; presents actionable alert | `MediaCompatibilityTest.testHardwareAccelerationModes()` |

---

## 4. Reproducible Fixture Generation Commands

Developers can generate test vectors locally using FFmpeg:

```bash
# 1. Standard 1080p H.264 + AAC MP4 fixture (5 seconds)
ffmpeg -f lavfi -i testsrc2=size=1920x1080:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 5 -c:v libx264 -preset ultrafast -c:a aac -b:a 192k \
  fixtures/standard_1080p_h264.mp4

# 2. 4K 10-bit HEVC HDR10 fixture with BT.2020 color matrix
ffmpeg -f lavfi -i testsrc2=size=3840x2160:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 5 -c:v libx265 -pix_fmt yuv420p10le -x265-params "colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc" \
  -c:a aac fixtures/4k_10bit_hevc_hdr.mp4

# 3. Dual-track audio MKV fixture (Spoken / Audio labels)
ffmpeg -f lavfi -i testsrc2=size=1280x720:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -f lavfi -i sine=frequency=880:sample_rate=48000 \
  -map 0:v -map 1:a -map 2:a -t 5 \
  -metadata:s:a:0 language=eng -metadata:s:a:0 title="English" \
  -metadata:s:a:1 language=jpn -metadata:s:a:1 title="Japanese" \
  -c:v libx264 -c:a aac fixtures/dual_audio_track.mkv
```
