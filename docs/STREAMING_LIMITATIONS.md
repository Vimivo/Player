# Streaming Limitations & Honest Protocol Scope (Milestones 53 & 54)

Vimivo maintains an honest, evidence-based stance regarding network streaming capabilities. We do not claim universal webpage compatibility or unsupported codec playback.

---

## 1. Webpage URLs Are Not Direct Media Streams

- **Limitation**: Pasting a standard HTML page URL (e.g. `https://youtube.com/watch?v=...`, `https://vimeo.com/...`, `https://tiktok.com/@...`) will not play.
- **Rationale**: Webpages contain HTML, CSS, JavaScript, and dynamic player frameworks. Vimivo is a native media player, not a headless web browser or web scraping bot.
- **Handling**: `StreamValidator` detects known portal hosts and `.html` extensions, displaying an explicit explanation to guide the user to direct media streams (`.mp4`, `.m3u8`, `.mpd`, `.mp3`, RTSP).

---

## 2. Platform-Specific Protocol Limitations

### iOS / iPadOS
- **MPEG-DASH (`.mpd`)**: Unsupported natively by AVFoundation `AVPlayer`. Reported as `UNSUPPORTED` rather than creating fragile fallback hacks.
- **RTSP (`rtsp://`)**: Unsupported natively by AVFoundation `AVPlayer`. Reported as `UNSUPPORTED`.
- **HLS (`.m3u8`)**: Fully supported with native hardware optimization.

### Android
- **MPEG-DASH**: Fully supported via `media3-exoplayer-dash`.
- **HLS**: Fully supported via `media3-exoplayer-hls`.
- **RTSP**: Supported via `media3-exoplayer-rtsp`.

### Desktop (macOS, Windows, Linux)
- **libmpv**: Fully supports HTTP/HTTPS progressive, HLS, DASH, RTSP, and Icecast/SHOUTcast radio.

---

## 3. DRM and Content Protection

- Vimivo does **not** bypass DRM, crack encryption keys, or circumvent subscription paywalls.
- Encrypted streams requiring Widevine, FairPlay, or PlayReady license servers without authorized app entitlements are recognized as protected and rejected cleanly rather than failing with confusing decoding errors.
