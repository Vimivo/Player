# Vimivo — Artwork & Thumbnail Cache Architecture

> [!NOTE]
> Authoritative documentation detailing image decoding, bounded caching, thumbnail generation, and disk storage policies across **Milestones 63 and 64**.

---

## 1. Executive Summary

Vimivo displays album artwork, artist imagery, and video frame thumbnails while adhering strictly to an **offline-first, privacy-first** paradigm. The application never queries external internet metadata services, scrapers, or album art databases. All visuals are extracted strictly from local media containers (embedded ID3 APIC frames, MP4 `covr` atoms, Vorbis comments) or captured directly from video keyframes.

---

## 2. Desktop Cache Architecture (`DesktopMediaCache`)

On Desktop targets (macOS, Windows, Linux), image storage is managed by `DesktopMediaCache`, rooted in the platform's standard per-user cache directory (`~/Library/Caches/dev.vimivo.player` on macOS, `%LOCALAPPDATA%\Vimivo\cache` on Windows, `~/.cache/vimivo` on Linux).

### Key Design Constraints
- **Maximum Dimension Bounding**:
  - Embedded Album Art: Capped at **1,024 px** on the longest edge.
  - Video Thumbnails: Capped at **640 px** on the longest edge.
- **Source Input Guard**: Source image buffers larger than **20 MB** are rejected to prevent decompression bombs.
- **Storage Limit**: Total directory cache size is capped at **256 MB**.
- **Deterministic LRU Trimming**: Every 32 store operations, the cache automatically enumerates files and trims least-recently-modified files until total usage falls below the 256 MB threshold.
- **Atomic File Writing**: Images are written to a `.cache-*.tmp` temporary file and moved to final SHA-256 destination using atomic rename (`StandardCopyOption.ATOMIC_MOVE`), eliminating corrupted partial reads.

---

## 3. Android Thumbnail Architecture (`AndroidThumbnailRepository`)

On Android targets:
- Image requests leverage Android's native `ContentResolver.loadThumbnail` for platform-managed MediaStore caching.
- Fallback paths decode embedded pictures via `MediaMetadataRetriever` with strict in-memory sampling options (`inSampleSize`), ensuring large camera images or high-res artwork never trigger an `OutOfMemoryError`.
- UI rendering uses `produceState` with `Dispatchers.IO`, ensuring zero main-thread hitch during image decodes.

---

## 4. UI Memory Containment

1. **Exact-Fit Layout Sizing**: Desktop artwork surfaces request sizes matching their display slot (`48.dp`, `64.dp`, `96.dp`, `120.dp`), avoiding full-resolution texture allocations.
2. **Coil 3 Integration**: On Compose Desktop, `AsyncImage` handles memory bitmap caching and disposal automatically when composables leave the composition tree.
3. **Palette Extraction Isolation**: Dominant color extraction for the Aurora Glass ambient backdrop is computed once upon track change and cached in memory. It is never recalculated during UI animation or recomposition frames.
