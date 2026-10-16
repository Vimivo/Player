# Vimivo Streaming Compatibility Matrix (Milestones 55 & 56)

> **Permanent Product Boundary:**  
> Vimivo is a local audio/video player with direct user-provided URL streaming.  
> It does **not** provide an online music/video library, remote content catalog, provider marketplace, or web scraping engine.

---

## 1. Five-Platform Protocol & Feature Capabilities

| Capability | Android (Media3) | iOS / iPadOS (AVPlayer) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Direct Progressive Audio (HTTP/HTTPS)** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Direct Progressive Video (MP4)** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Direct Progressive Video (MKV/WebM)** | RUNTIME VERIFIED | UNSUPPORTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **HLS VOD (`.m3u8`)** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **HLS Live Broadcast** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Low-Latency HLS (LL-HLS)** | PARTIAL (Server-req.) | RUNTIME VERIFIED | PARTIAL (Readahead) | PARTIAL (Readahead) | PARTIAL (Readahead) |
| **MPEG-DASH VOD (`.mpd`)** | RUNTIME VERIFIED | UNSUPPORTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **MPEG-DASH Live Broadcast** | RUNTIME VERIFIED | UNSUPPORTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **RTSP Live (`rtsp://`, `rtsps://`)** | RUNTIME VERIFIED | UNSUPPORTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Internet Radio (Icecast / SHOUTcast)** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Live Latency Modes (Balanced/Low/Stable)** | IMPLEMENTED | IMPLEMENTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Session Generation Isolation** | IMPLEMENTED | IMPLEMENTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **User Stop / Pause Intent Preservation** | IMPLEMENTED | IMPLEMENTED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Audio Track Selection** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Subtitle Track Selection** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Live Sliding Window & DVR Seeking** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Background Audio Playback** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **System Media Controls (MediaSession/MPRIS/NowPlaying)** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |
| **Bounded Reconnection & Backoff** | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED | RUNTIME VERIFIED |

---

## 2. Platform Native Engine Mapping

- **Android**: `androidx.media3:media3-exoplayer` (with `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-exoplayer-rtsp`).
- **iOS / iPadOS**: Native `AVFoundation` / `AVPlayer` with asynchronous property loading. Standard `AVPlayer` does not decode generic MPEG-DASH or RTSP without external transcoders or unlicensed non-native libraries; Vimivo reports these as unsupported rather than introducing brittle fake players.
- **macOS Desktop**: `libmpv` dynamic library integration embedded via JNA, leveraging VideoToolbox hardware acceleration and Cocoa surface integration.
- **Windows Desktop**: `libmpv.dll` integration with Direct3D 11 (`d3d11va`) hardware video decoding.
- **Linux Desktop**: `libmpv.so` integration with VA-API / VDPAU decoding and MPRIS D-Bus interface.

---

## 3. Strict Scope & Privacy Policy

1. **No Content Catalog**: Vimivo does not query remote station lists, trending videos, online directories, or recommendation engines.
2. **Direct Addressing**: Network playback is initiated strictly by direct user input of a validated media URI.
3. **No Web Scraping**: URLs pointing to portal pages (e.g., YouTube, Vimeo, TikTok, HTML pages) are rejected with clear user explanations rather than running unauthorized web scraping scripts.
4. **Header & Credential Security**: Stream request headers are checked against CRLF injection. Tokens and signed URL parameters are redacted in diagnostic displays and logs.
