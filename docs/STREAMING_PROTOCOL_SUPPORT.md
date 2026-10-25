# Streaming Protocol Support Matrix (Milestones 53 & 54)

This matrix records verified protocol and container support across all target operating systems. Vimivo claims support only where native decoders and network pipelines exist without third-party web scrapers.

---

## 1. Cross-Platform Protocol Capabilities

| Protocol / Container | Android (Media3) | Android (libmpv; was LibVLC) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | iOS (AVPlayer) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **HTTP Progressive Audio** (MP3, AAC, FLAC, Opus, WAV) | Supported | Supported | Supported | Supported | Supported | Supported |
| **HTTPS Progressive Audio** | Supported | Supported | Supported | Supported | Supported | Supported |
| **HTTP Progressive Video** (MP4, MKV, WebM) | Supported | Supported | Supported | Supported | Supported | Supported (MP4 only) |
| **HTTPS Progressive Video** | Supported | Supported | Supported | Supported | Supported | Supported (MP4 only) |
| **HLS VOD** (`.m3u8`) | Supported | Supported | Supported | Supported | Supported | Supported |
| **HLS Live Broadcast** (`.m3u8`) | Supported | Supported | Supported | Supported | Supported | Supported |
| **MPEG-DASH VOD** (`.mpd`) | Supported | Supported | Supported | Supported | Supported | Unsupported (No native DASH) |
| **MPEG-DASH Live** (`.mpd`) | Supported | Supported | Supported | Supported | Supported | Unsupported (No native DASH) |
| **RTSP Live** (`rtsp://`, `rtsps://`) | Supported | Supported | Supported | Supported | Supported | Unsupported (No native RTSP) |
| **Internet Radio** (Icecast / SHOUTcast) | Supported | Supported | Supported | Supported | Supported | Supported (HTTP/HTTPS) |

---

## 2. Container & Codec Verification

### Audio Codecs
- **MP3 (`audio/mpeg`)**: Native decoding on all 6 targets.
- **AAC (`audio/aac`, `audio/mp4`)**: Native hardware decoding on all targets.
- **FLAC (`audio/flac`)**: Lossless network streaming supported on Android, Desktop, and iOS.
- **Opus (`audio/opus`, `audio/ogg`)**: Supported natively on Android Media3 and Desktop libmpv.
- **Ogg Vorbis (`audio/ogg`)**: Supported on Android and Desktop libmpv.

### Video Containers
- **MP4 / MOV (`video/mp4`, `video/quicktime`)**: Hardware accelerated decoding on all platforms.
- **Matroska / MKV (`video/x-matroska`)**: Supported on Desktop libmpv and Android (Media3 & libmpv). iOS native AVPlayer does not decode MKV without remuxing.
- **WebM (`video/webm`)**: Supported on Android (VP8/VP9) and Desktop libmpv.

---

## 3. Webpage vs. Direct Stream Rejection

Webpages, social share links, and portal URLs are explicitly detected and rejected with clear explanations:

| Input Pattern | Classification | Result & Explanation |
| :--- | :--- | :--- |
| `https://youtube.com/watch?v=...` | Portal Webpage | Rejected: "This link points to an online video platform webpage (youtube.com), not a direct playable stream." |
| `https://vimeo.com/...` | Portal Webpage | Rejected: "This link points to a vimeo.com webpage, not a direct playable stream." |
| `https://tiktok.com/@...` | Social Share Page | Rejected: "This link points to a tiktok.com webpage, not a direct playable stream." |
| `https://example.com/stream.html` | HTML Document | Rejected: "The URL references an HTML/webpage file (.html), not a media stream." |
| `https://server.test/audio.mp3` | Direct Audio | Accepted: Direct progressive audio stream. |
| `https://server.test/live.m3u8` | HLS Manifest | Accepted: HTTP Live Streaming manifest. |
| `rtsp://cam.test:8554/live` | RTSP Stream | Accepted: Direct network stream. |
