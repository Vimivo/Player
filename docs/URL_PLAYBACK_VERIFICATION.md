# Vimivo — Direct Network Media & URL Playback Verification

**Milestones**: 57 & 58  
**Target Release**: `1.0.0-rc.1`  
**Git HEAD**: `0d7ecba`  
**Date**: 2026-10-16  

---

## 1. Product Scope & Philosophy

Vimivo provides direct network media playback strictly for **user-provided URLs**:
- **Supported**: Direct HTTP/HTTPS audio/video files, HLS live and VOD streams, supported DASH manifests, supported RTSP streams, and Internet Radio (Icecast/Shoutcast).
- **Prohibited & Excluded**: Online catalogs, content aggregators, web scrapers, YouTube/TikTok downloaders, cloud video transcoding services, mandatory accounts, and DRM circumvention.

---

## 2. Protocol & Format Verification Matrix

### 2.1 Direct Network Media Files

| Media Type | Protocol / Scheme | Container / Codecs | HTTP Capabilities | Verified Native Behavior | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Direct Audio URL** | `https://`, `http://` | MP3, AAC, M4A, FLAC, Opus | Full GET, HTTP Range (206) | Buffer caching, instant seek, audible audio | **PASS** |
| **Direct Video URL** | `https://`, `http://` | MP4 (H.264/AAC), WebM (VP9/Opus) | HTTP Range (206 Partial Content) | Hardware decoding, frame rendering, AV sync | **PASS** |
| **Internet Radio** | `https://`, `http://` | MP3, AAC, Ogg streams | Continuous chunked transfer | Indefinite continuous playback, background audio | **PASS** |

### 2.2 Adaptive Streaming (HLS & DASH)

| Protocol | Variant | Manifest Type | Platform Backends | Key Verification | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **HLS (HTTP Live Streaming)** | VOD | Master & Media Playlist (`.m3u8`) | Media3 / libmpv / AVPlayer | Multi-bitrate switching, accurate total duration | **PASS** |
| **HLS (HTTP Live Streaming)** | Live | Sliding window manifest | Media3 / libmpv / AVPlayer | Live-edge synchronization, disabled invalid seeking | **PASS** |
| **DASH** | Static (VOD) | `.mpd` XML manifest | Media3 DASH / libmpv DASH | Multi-representation switching, audio/video demux | **PASS** (Android/Desktop) |
| **DASH** | Dynamic (Live) | Sliding segment timeline | Media3 DASH / libmpv DASH | Live boundary tracking (Not supported on native iOS) | **PASS** (Android/Desktop) |

### 2.3 Real-Time Streaming (RTSP)

| Protocol | Transport | Video / Audio Codecs | Platform Support | Behavior Under Packet Drop | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RTSP** | TCP / UDP RTP | H.264 / AAC, G.711 | Media3 RTSP / libmpv | Low-latency stream connect, clean teardown | **PASS** (Android/Desktop) |

> [!NOTE]
> Standard Apple AVPlayer does not natively support arbitrary RTSP or DASH manifests. Vimivo reports this capability restriction honestly in `StreamValidator` rather than masking it with unverified fallbacks.

---

## 3. Network Interruption, Recovery, and Session Isolation

### 3.1 Bounded Exponential Backoff Policy

All network media reconnection is governed by `StreamReconnectionPolicy`:
- **Attempt 1**: 1,000ms delay.
- **Attempt 2**: 2,000ms delay.
- **Attempt 3**: 4,000ms delay.
- **Max Delay Cap**: 6,000ms.
- **Max Retries**: 3 attempts before declaring fatal failure (`StreamFailureReason.CONNECTION_LOST`).
- **Immediate Cancellation**: User explicit `stop()` or switching to another media source immediately cancels pending reconnection timers and releases network resources.

### 3.2 Session Isolation & Stale Callback Prevention

Each playback request increments an atomic `sessionGeneration` counter:
- Delayed network error callbacks from abandoned streams check `generation == currentSessionGeneration`.
- Stale network packets or delayed buffer-underrun events from previous sources are cleanly discarded.
- Verified in `PlaybackSourceSwitchingTest` and `StreamSessionLifecycleTest`.

---

## 4. Verification Test Suites

Automated verification is established in:
- `desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/playback/PlaybackSourceSwitchingTest.kt`
- `desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/playback/PlaybackFailureInjectionTest.kt`
- `desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/playback/EndToEndPlaybackUserJourneysTest.kt`
- `shared/src/desktopTest/kotlin/dev/vimivo/core/streaming/StreamSessionLifecycleTest.kt`
- `shared/src/desktopTest/kotlin/dev/vimivo/core/streaming/StreamValidatorTest.kt`
