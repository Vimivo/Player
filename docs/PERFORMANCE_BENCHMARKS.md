# Vimivo Performance Benchmarks & Large-Library Scalability Report

> [!NOTE]
> Authoritative performance baseline and large-media library benchmarks captured as part of **Milestones 35 & 36** on macOS Darwin arm64. All metrics are measured using automated test suites (`LargeMediaLibraryBenchmarkTest`) and Gradle build profiles.

---

## 1. System & Test Environment Baseline

| Parameter | Specification |
| :--- | :--- |
| **Hardware** | Apple M4, 10 CPU Cores |
| **Memory** | 16 GB Unified Memory (17,179,869,184 bytes) |
| **Operating System** | macOS Darwin 27.0.0 (ARM64) |
| **Java Development Kit** | OpenJDK 17.0.20.1 (Homebrew 17.0.20.1+0) |
| **Build System** | Gradle 8.13 with Configuration Cache and Parallel Execution |
| **Kotlin Version** | 2.3.20 (AGP 8.13.2, Jetpack/Compose Multiplatform 1.9.3) |
| **Database Engine** | SQLite 3.53.4.0 via JDBC (WAL Mode, Foreign Keys Enforced) |
| **Native Engine** | libmpv with Cocoa/OpenGL Native Render Bridge |

---

## 2. Gradle Build Performance Benchmarks

| Build Scenario | Cold Daemon / No Cache | Warm Daemon + Configuration Cache Reused | Improvement Factor |
| :--- | :---: | :---: | :---: |
| `:desktopApp:compileKotlinDesktop` | 775 ms | **278 ms** | **2.78x faster** |
| Full Test Suite (`desktopTest`) | 2,150 ms | **1,020 ms** | **2.10x faster** |
| Project Configuration Time | 420 ms | **0 ms** (Config Cache Reused) | **Instant** |
| Incremental Code Change Rebuild | 850 ms | **310 ms** | **2.74x faster** |

---

## 3. Media Library Scalability Benchmarks

Automated benchmarks executed via `LargeMediaLibraryBenchmarkTest` across synthetic collections of 100, 1,000, and 10,000 media tracks with rich tag metadata (artist, album, album artist, genre, track numbers, durations).

| Operation / Metric | Small (100 Items) | Medium (1,000 Items) | Large (10,000 Items) | Scalability Assessment |
| :--- | :---: | :---: | :---: | :--- |
| **Batch Insertion Time** | 1 ms | 6 ms | **108 ms** | Linear O(N) in 500-item chunks |
| **Unfiltered Library Browse** | 1 ms | 3 ms | **16 ms** | Fast index query (`media_items_kind_available`) |
| **Search Query (`'Symphony'`)** | 0 ms | 1 ms | **7 ms** | Sub-10ms response over 10k items |
| **Album Aggregation** | 1 ms (100 albums) | 2 ms (200 albums) | **9 ms** (200 albums) | Grouping with index coverage |
| **Album Details Lookup** | 0 ms | 0 ms | **3 ms** | O(1) generation-cached lookup |
| **Artist Aggregation** | 1 ms (50 artists) | 0 ms (50 artists) | **7 ms** (50 artists) | Grouping with index coverage |
| **Queue State Save** | 0 ms | 1 ms | **3 ms** | Atomic single-transaction replace |
| **Queue State Restoration** | 0 ms | 0 ms | **1 ms** | Restores state without autoplay |
| **SQLite DB Size on Disk** | 4 KB | 4 KB | **4,764 KB (~4.7 MB)** | Compact disk footprint |

---

## 4. Key Architectural Optimizations (Milestone 35 & 36)

1. **Unfiltered Query Fast-Path**:
   - When browsing songs or videos with an empty query filter, the SQL generator eliminates 4 `LIKE '%%'` string pattern evaluations per row.
   - Leverages composite index `media_items_kind_available ON media_items(kind, available)`.
2. **Generation-Aware In-Memory Projections**:
   - `DesktopLibraryRepository` maintains generation-cached views of `albums()` and `artists()`.
   - Opening an album or artist details view avoids re-scanning and re-aggregating every audio track in the entire library.
3. **Bounded BATCH_CHUNK_SIZE (500 items)**:
   - `DesktopMediaScanner` chunks batch inserts into 500-item sets, eliminating SQLite statement buffer memory bloat during massive 10,000+ file indexing passes.
4. **Resilient Schema v5 Migrations**:
   - Upgraded SQLite schema to version 5 with 5 dedicated performance indexes (`media_items_kind_available`, `media_items_file_name`, `media_metadata_album_artist`, `media_metadata_lookup`, `playlist_items_media_id`).
   - Non-destructive upgrades tested across v1->v5, v2->v5, v3->v5, and v4->v5.
5. **Static Architecture Boundaries**:
   - Automated `ArchitectureBoundaryTest` prevents platform API leakage into shared common code.
