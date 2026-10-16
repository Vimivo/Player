# Live Streaming Latency & Buffer Optimization (Milestones 55 & 56)

## 1. Live Latency Measurement & Components

In live video and audio streaming, glass-to-glass latency is determined by distinct architectural layers:
1. **Manifest Publication Delay**: The time between live capture and manifest reload availability.
2. **Media Segment Duration**: Chunks are typically 2.0s to 6.0s in traditional HLS/DASH.
3. **Client Demuxer Readahead Buffer**: Seconds of compressed media parsed in advance of presentation.
4. **Decoder & Jitter Buffer**: Uncompressed frames held in the audio/video rendering queue.
5. **Renderer Display Sync**: Hardware presentation timestamps (V-Sync).

Vimivo distinguishes measured client distance behind the live edge from total glass-to-glass delay. Where stream timestamps are available, distance behind the live edge is tracked; where unavailable, relative client readahead buffers are tuned.

---

## 2. Live Latency Modes

Vimivo provides three capability-aware live latency configurations:

| Mode | Readahead Target | Max Buffer Bytes | Use Case & Trade-offs |
| :--- | :---: | :---: | :--- |
| **BALANCED** (Default) | 5.0 seconds | 32 MB (`33,554,432`) | Reliability-first default. Smooth playback under normal domestic broadband and 4G/5G mobile connections with minimal rebuffering. |
| **LOW_LATENCY** | 1.5 seconds | 8 MB (`8,388,608`) | Prioritizes minimal delay behind live edge for real-time sports, news broadcasts, and low-latency RTSP monitoring feeds. Requires a low-jitter connection. |
| **STABLE_PLAYBACK** | 10.0 seconds | 64 MB (`67,108,864`) | Maximum buffering resilience for weak Wi-Fi, high-latency satellite, commuter trains, or congested public networks. Trades live-edge proximity for zero rebuffering. |

---

## 3. Dynamic Engine Adaptation (`MpvPlayerEngine`)

In `MpvPlayerEngine`, switching latency modes does not require recreating the native player context or detaching the video surface:

```kotlin
override fun setLiveLatencyMode(mode: LiveLatencyMode): Result<Unit> = runCatching {
    check(!closed) { "Player is closed" }
    liveLatencyMode = mode
    if (context != null) {
        setProperty("demuxer-readahead-secs", mode.readaheadSeconds.toString())
        setProperty("demuxer-max-bytes", mode.maxBufferBytes.toString())
    }
}
```

When a network stream is opened, the active `liveLatencyMode` is applied upfront during `open(media)`. Users can switch modes dynamically via the Open Network Stream dialog or stream preferences.
