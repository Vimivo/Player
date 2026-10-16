# Streaming Test Infrastructure & Deterministic Fixtures (Milestones 55 & 56)

## 1. Overview

To avoid dependency on external, unreliable internet streams and prevent network flakiness in CI, Vimivo provides an in-process, deterministic HTTP media server: `TestStreamServer`.

Located at:
`desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/streaming/TestStreamServer.kt`

---

## 2. Supported Test Endpoints & Protocols

| Endpoint | Protocol / Type | Characteristics & Capabilities |
| :--- | :--- | :--- |
| `/media/audio.mp3` | Direct Audio | Serves mock MP3 frames. Supports standard GET (HTTP 200) and byte-range requests (HTTP 206 Partial Content) with `Accept-Ranges: bytes` and `Content-Range`. |
| `/media/video.mp4` | Direct Video | Serves mock MP4 frames with range request support. |
| `/hls/vod.m3u8` | HLS VOD | Static playlist with `#EXTM3U`, target duration 2s, segment list, and `#EXT-X-ENDLIST`. |
| `/hls/live.m3u8` | HLS Live | Dynamic sliding window manifest without `#EXT-X-ENDLIST`. Each poll increments `#EXT-X-MEDIA-SEQUENCE: N`, simulating real live broadcast window progression. |
| `/hls/*.ts` | HLS Segments | Returns mock MPEG-2 Transport Stream (TS) packets with sync bytes (`0x47`). |
| `/dash/manifest.mpd` | MPEG-DASH | Valid XML MPD with `minBufferTime`, `Period`, and `AdaptationSet`. |
| `/fault/401` | Fault Injection | Returns HTTP 401 Unauthorized. |
| `/fault/403` | Fault Injection | Returns HTTP 403 Forbidden. |
| `/fault/404` | Fault Injection | Returns HTTP 404 Not Found. |
| `/fault/408` | Fault Injection | Returns HTTP 408 Request Timeout. |
| `/fault/429` | Fault Injection | Returns HTTP 429 Too Many Requests. |
| `/fault/500` | Fault Injection | Returns HTTP 500 Internal Server Error. |
| `/fault/redirect` | Redirect Testing | Returns HTTP 302 redirecting to `/media/audio.mp3`. |
| `/fault/redirect-loop` | Redirect Loop | Returns HTTP 302 redirecting recursively to test bounded redirect handling. |

---

## 3. Usage Example in Kotlin Tests

```kotlin
val server = TestStreamServer()
try {
    server.start()
    val mediaUrl = "${server.baseUrl}/media/audio.mp3"
    // Execute deterministic assertions against localhost
} finally {
    server.close()
}
```

This guarantees 100% offline, reproducible unit and integration testing without leaking test traffic to external networks.
