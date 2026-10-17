# Vimivo — Direct URL Streaming & Network Efficiency

> [!NOTE]
> Authoritative engineering report covering user-provided direct URL streaming, network request efficiency, buffering memory bounds, and bounded reconnection policies across **Milestones 63 and 64**.

---

## 1. Executive Summary

Vimivo supports playback of user-provided direct media URLs (direct HTTP/HTTPS audio/video files, HLS live and VOD streams, and Internet Radio streams). It strictly excludes online catalog services, scrapers, and remote recommendations.

Network efficiency is maximized by:
1. Bounding demuxer cache and readahead buffers based on stream type.
2. Enforcing a bounded exponential-backoff reconnection policy with instant cancellation on user Stop.
3. Isolating rapid source switches via atomic session generation counters.
4. Defending against CRLF injection, credential leakage, and SSRF attacks.

---

## 2. Stream Buffer Profiles & Memory Limits

Rather than using a single static buffer configuration, Vimivo dynamically adapts buffer sizes to match the playback context:

| Latency Profile | Target Scenario | Demuxer Cache Limit | Readahead Target | Live-Edge Delay |
| :--- | :--- | :---: | :---: | :---: |
| **`LOW_LATENCY`** | Live internet radio, interactive broadcasts | **8 MB** | 1.5 s | < 3.0 s |
| **`BALANCED`** (Default) | Standard HLS live streams, network video | **32 MB** | 5.0 s | ~ 6.0 s |
| **`STABLE_PLAYBACK`** | High-bitrate 4K VOD, unstable Wi-Fi/cellular | **64 MB** | 15.0 s | N/A (VOD) |

*Safety Guard*: Demuxer cache memory is hard-capped at **64 MB** maximum, ensuring network media playback never causes high-memory pressure or app termination on low-RAM mobile devices.

---

## 3. Bounded Reconnection Policy (`StreamReconnectionPolicy`)

When network disruptions occur, Vimivo executes a bounded recovery cycle:
- **Maximum Retries**: Bounded to **5 attempts**.
- **Backoff Formula**: Exponential backoff with jitter:
  $$\Delta t = \min(1000 \text{ ms} \times 2^{\text{attempt}}, 16000 \text{ ms})$$
- **Terminal States**: Non-retryable HTTP status codes (`401 Unauthorized`, `403 Forbidden`, `404 Not Found`) immediately halt reconnection attempts and surface actionable user feedback.
- **Immediate Cancellation**: When the user taps **Stop** or switches tracks, `userStopped` is flagged and the active coroutine reconnection job is cancelled immediately. No orphaned HTTP retry connections execute in the background.

---

## 4. Session Pre-emption & Race Condition Prevention

Rapid source changes (e.g. tapping 10 internet radio stations in quick succession) are protected by an atomic generation counter:
```kotlin
private val sessionGeneration = AtomicLong(0L)
```
Every `open(source)` increments this counter. When network callbacks, manifest probes, or socket connections complete, their response is discarded if their generation does not match the active session. This eliminates stale audio resurrection and audio engine conflicts.
