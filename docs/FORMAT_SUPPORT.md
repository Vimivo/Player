# Vimivo — Supported Media Format & Codec Matrix

This document provides the verified codec, container, metadata extraction, and embedded tag editing capabilities across all five supported operating systems.

---

## 1. Audio Containers & Codecs

| Format / Container | Extension | Playback Support | Metadata Extraction | Embedded Tag Writing | Notes |
|---|---|---|---|---|---|
| **MP3** | `.mp3` | Android, iOS, macOS, Win, Linux | ID3v1, ID3v2.3, ID3v2.4 | Yes (jaudiotagger / MP3) | Universal support |
| **FLAC** | `.flac` | Android, iOS, macOS, Win, Linux | Vorbis Comments, Pictures | Yes (Vorbis Comments) | Lossless audio |
| **Ogg Vorbis** | `.ogg` | Android, macOS, Win, Linux | Vorbis Comments | Yes (Vorbis Comments) | Supported natively |
| **M4A / AAC** | `.m4a`, `.aac`| Android, iOS, macOS, Win, Linux | MP4 Atoms / iTunes Metadata | Yes (MP4 Atoms) | Native Apple & Android |
| **WAV** | `.wav` | Android, iOS, macOS, Win, Linux | RIFF INFO & ID3 Chunk | Yes (ID3 chunk) | Uncompressed PCM |
| **Opus** | `.opus` | Android, macOS, Win, Linux | Ogg Opus Comments | Read-Only (Overrides in DB) | Low-latency audio |
| **AIFF** | `.aiff`, `.aif`| iOS, macOS, Win, Linux | AIFF ID3 chunk | Read-Only (Overrides in DB) | Apple uncompressed PCM |
| **WMA** | `.wma` | Windows, macOS (mpv), Linux | ASF Header | Read-Only (Overrides in DB) | Legacy Windows audio |

*Note: For formats with Read-Only tag status, Vimivo provides Mode A (Database Overrides), allowing full custom titles, artists, albums, genres, years, and artwork to be assigned without altering the physical file.*

---

## 2. Video Containers & Codecs

| Container | Typical Codecs | macOS / Win / Linux (libmpv) | Android (Media3) | iOS (AVFoundation) | Hardware Acceleration |
|---|---|---|---|---|---|
| **MP4 / M4V** | H.264 (AVC), H.265 (HEVC), AV1 | Yes | Yes | Yes | GPU (VideoToolbox / MediaCodec / D3D11VA / VAAPI) |
| **MKV** (Matroska) | H.264, H.265, VP9, AV1 | Yes | Yes (Media3 extractor) | Partial (libmpv needed) | GPU (Hardware decoders) |
| **WebM** | VP8, VP9, AV1, Vorbis, Opus | Yes | Yes | Partial | GPU (VAAPI, NVDEC, D3D11) |
| **MOV** (QuickTime)| ProRes, H.264, HEVC | Yes | Yes | Yes | VideoToolbox / Hardware |
| **AVI** | MPEG-4, DivX, XviD | Yes | Fallback / Controlled | Partial | Software / Fallback |

---

## 3. Subtitles & Closed Captions

| Format | Extension | Styling Support | Delay Adjustment | Notes |
|---|---|---|---|---|
| **SubRip (SRT)** | `.srt` | Text size, margin, color | Yes (±5000ms) | Auto-discovered if named identically next to video |
| **Advanced SubStation Alpha** | `.ass`, `.ssa` | Full ASS vector styling & fonts | Yes (±5000ms) | Rendered natively by `libass` on desktop |
| **WebVTT** | `.vtt` | Basic styling | Yes (±5000ms) | Web standard subtitle format |
| **Embedded Subtitles** | MKV/MP4 internal | Format-dependent | Yes | Selected via Subtitle track menu |

---

## 4. Playlists

| Format | Extension | Import | Export | Notes |
|---|---|---|---|---|
| **Standard M3U** | `.m3u` | Yes | Yes | Relative & absolute path support |
| **Extended M3U8** | `.m3u8` | Yes | Yes | Full UTF-8 Unicode & Bengali script preservation |
| **Smart Playlists** | N/A (Internal) | Built-in | Dynamic | Serialized AST rules stored in SQLite `smart_playlists` |
