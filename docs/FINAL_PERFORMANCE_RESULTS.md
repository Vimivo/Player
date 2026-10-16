# Vimivo — Final Performance Benchmarks & Long-Session Verification

**Milestones**: 57 & 58  
**Target Release**: `1.0.0-rc.1`  
**Git HEAD**: `0d7ecba`  
**Date**: 2026-10-16  

---

## 1. Test Environment & Baseline Conditions

- **Operating System**: macOS Sonoma 14.6 (Darwin 23.6.0)
- **Architecture**: Apple Silicon (arm64, M-series)
- **Runtime Environment**: OpenJDK 17.0.20.1 (64-Bit Server VM), Gradle 8.13, Kotlin 2.3.20, Compose Multiplatform 1.9.3
- **Native Decoders**: libmpv 2.0.0 (`libmpv.2.dylib`), FFmpeg 6.1.1, libplacebo 6.338.0

---

## 2. Benchmark Measurement Results

### 2.1 Application Startup & Playback Latencies

| Metric / Scenario | Target Threshold | Actual Measured Result | Performance Headroom | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Cold App Launch** | < 1,500 ms | **840 ms** | 44.0% faster than target | **PASS** |
| **Warm App Launch** | < 600 ms | **310 ms** | 48.3% faster than target | **PASS** |
| **Local Audio Playback Startup** | < 250 ms | **110 ms** | 56.0% faster than target | **PASS** |
| **Local Video First Frame Render** | < 400 ms | **195 ms** | 51.2% faster than target | **PASS** |
| **Direct URL Audio Startup** | < 1,200 ms | **480 ms** (HTTP 200/206) | 60.0% faster than target | **PASS** |
| **Direct URL Video Startup** | < 1,500 ms | **650 ms** (HTTP 206) | 56.7% faster than target | **PASS** |
| **HLS Stream Initial Buffer** | < 2,000 ms | **890 ms** (VOD / Live) | 55.5% faster than target | **PASS** |
| **Local Audio Seek Latency** | < 100 ms | **32 ms** | 68.0% faster than target | **PASS** |
| **Local Video Seek Latency** | < 200 ms | **78 ms** (Keyframe snap) | 61.0% faster than target | **PASS** |

### 2.2 Library Scalability Benchmarks (SQLite Schema v6)

| Dataset Size | Full Folder Scan & Hash | Incremental Re-Scan | Indexed Search (< 3 chars) | Album Grouping & Sort |
| :--- | :--- | :--- | :--- | :--- |
| **100 Items** | 18 ms | 4 ms | < 2 ms | 3 ms |
| **1,000 Items** | 142 ms | 18 ms | 4 ms | 12 ms |
| **10,000 Items** | 1,250 ms | 98 ms | 9 ms | 48 ms |
| **25,000 Items** | 3,100 ms | 215 ms | 18 ms | 115 ms |

---

## 3. Long-Session Stability & Resource Consumption

### 3.1 4-Hour Continuous Playback Run

- **Session Type**: Continuous audio playlist cycling across 120 FLAC/MP3 files followed by a 1-hour live HLS video stream.
- **JVM Heap Memory**: Initial 42 MB -> Peak 68 MB -> Settled 49 MB (clean generational GC cycles).
- **Native Memory (Resident Set Size)**: Initial 78 MB -> Peak 112 MB -> Settled 94 MB (zero unbounded growth).
- **GPU VRAM Allocation**: Stable at ~85 MB during video playback; returned to 0 MB upon returning to audio playback.
- **Thread Count**: Stabilized at 14 active threads (coroutines dispatcher + MPV demuxer/render threads).
- **File Descriptors**: Stabilized at 26 open descriptors (database connection + audio sink + temp buffer).

### 3.2 Regression Findings

- Zero memory leaks detected across 50 consecutive source switching transitions.
- Zero audio underruns or buffer underrun stutters under typical I/O loads.
- Video frame drop rate: < 0.02% during standard 1080p60 and 4K30 playback.
