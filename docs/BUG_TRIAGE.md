# Vimivo Bug Triage & Defect Remediation Register

## 1. Triage Philosophy & Standards

For Vimivo, defect triage is grounded in real reproducible evidence, automated regression coverage, and zero regressions of user data integrity.
We do not artificially downgrade defects to permit unverified releases, nor do we suppress exceptions or fabricate pass rates.

---

## 2. Severity Classification Matrix

| Level | Classification | Impact Threshold | Policy & Action |
| :---: | :--- | :--- | :--- |
| **P0** | **Critical** | Data corruption, security vulnerability, app crash on bootstrap, playback engine deadlocks, unsafe database migration. | **Release Blocker**. Code freeze entered. Requires root-cause analysis, regression test, and immediate verified fix. |
| **P1** | **High** | Reproducible playback failure, persistent queue desynchronization, major accessibility blockers, native memory leaks (>100MB/hr). | **Release Blocker**. Must be resolved or explicitly accepted with documented workaround by release lead. |
| **P2** | **Medium** | Minor format parsing failures on rare codecs, non-critical UI overflow, secondary settings glitch. | Addressed during normal stabilization cycles. |
| **P3** | **Low** | Minor cosmetic defect, alignment or margin inconsistency, typography polish. | Backlog polish. |

---

## 3. Investigated Defects & Root-Cause Remediation Ledger

### Defect VIM-BUG-001 (P0): Potential SQLite Foreign Key Violation during In-Flight Queue Deletion
- **Platform**: All platforms (Shared SQLite database).
- **Subsystem**: Database Persistence (`DatabaseMigrations.kt`, `MediaDao`).
- **Symptom**: When a user deleted a media track that was actively referenced in an active playback queue entry, older SQLite configurations without PRAGMA foreign_keys = ON risked dangling pointers or orphan queue references.
- **Root Cause**: SQLite does not enable foreign key enforcement by default on new database connections unless explicitly configured on each connection open.
- **Correction**: Configured explicit `PRAGMA foreign_keys = ON;` in `DatabaseMigrations.kt` connection initializer and wrapped multi-table queue updates in atomic transactions.
- **Regression Test**: `DesktopDatabaseMigrationTest.kt` -> `foreignKeyConstraintsAreEnforced()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-002 (P1): Diagnostic Exporter Permitted File Overwrite to macOS `/etc` via Symlink
- **Platform**: macOS Desktop (`DesktopDiagnosticExporter.kt`).
- **Subsystem**: Diagnostics & Export Subsystem.
- **Symptom**: Exporting to `/etc/vimivo-leak.md` could bypass simple path prefix checks because on macOS, `/etc` is a symbolic link pointing to `/private/etc`.
- **Root Cause**: The prefix filter only checked `File.absolutePath` rather than resolving both `canonicalPath` and `absolutePath` across system prefixes.
- **Correction**: Resolved `canonicalPath` with fallback to `absolutePath`, and expanded protected prefixes to include `/private/etc`, `/private/var/root`, `/var/root`, and `C:\Windows`.
- **Regression Test**: `DesktopDiagnosticExporterTest.kt` -> `refusesToExportToProtectedSystemLocations()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-003 (P1): Incomplete Credential Pair Redaction in Diagnostic Exceptions
- **Platform**: All platforms (`SafeLogger.kt`, `PrivacySafeDiagnostics.kt`).
- **Subsystem**: Privacy & Logging Subsystem.
- **Symptom**: Exception messages with format `token=secret123` outside of URL query parameters were not redacted by URL regex alone.
- **Root Cause**: `SENSITIVE_QUERY_PARAM_REGEX` required `?` or `&` query parameter delimiters, omitting raw space-delimited key-value credential pairs in exception messages.
- **Correction**: Added `SENSITIVE_KEY_VALUE_REGEX` to `SafeLogger.sanitize()` to capture `token=...`, `key=...`, `secret=...`, `password=...` anywhere in diagnostic text.
- **Regression Test**: `PrivacySafeDiagnosticsTest.kt` -> `errorRecordingSanitizesExceptionMessageAndStackTrace()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-004 (P1): Missing Media Handling During Active Playback
- **Platform**: Desktop & Android (`PlayerEngine`, `MediaItem`).
- **Subsystem**: Playback Controller.
- **Symptom**: Unplugging an external drive while playing a track could cause unhandled I/O exceptions in downstream decoders or loop rescanning.
- **Root Cause**: Playback state machine lacked an explicit transition to `PlaybackState.Error` when encountering `ENOENT` / file unreadable errors.
- **Correction**: Handled I/O errors gracefully in `PlayerEngine`, surfaced descriptive UI notice, and safely advanced or stopped without modifying library records.
- **Regression Test**: `DesktopUserJourneysTest.kt` -> `journey7_FaultInjectionAndResilience()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-005 (P1): Desktop libmpv Polling Race Condition Overwrote Media State on Rapid Track Switching
- **Platform**: Desktop (macOS, Windows, Linux) (`MpvPlayerEngine.kt`).
- **Subsystem**: Desktop libmpv Playback Engine.
- **Symptom**: Rapid switching across multiple audio tracks in tight succession resulted in `engine.state.value.media` reflecting an earlier track (e.g. track 17 instead of track 20) or showing outdated media metadata.
- **Root Cause**: `open()` initiated asynchronous polling without atomically canceling previous polling jobs under `nativeLock`, and `startPolling` captured a stale `previous = mutableState.value` before querying JNA properties, subsequently copying this stale reference over newer state emitted by a concurrent `open()`.
- **Correction**: Synchronized session generation advancement and job cancellation atomically within `synchronized(nativeLock)`. Refactored state updates to use `mutableState.update` guarded by `sessionGeneration == expectedSession`, preventing stale asynchronous polling iterations from corrupting active state.
- **Regression Test**: `LongSessionStabilityTest.kt` -> `testLongSessionRapidSourceSwitchingSafety()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-006 (P2): URL Embedded User Credentials Leaked in Raw Diagnostic Export
- **Platform**: All platforms (`SafeLogger.kt`).
- **Subsystem**: Privacy & Logging Subsystem.
- **Symptom**: URLs containing embedded user info credentials (`http://username:password@domain.com/stream.m3u8`) retained credentials in error logs because regexes only matched query parameters (`?token=...`).
- **Root Cause**: `SafeLogger.redactUrl` lacked an embedded user-info credential regex (`EMBEDDED_CREDENTIALS_REGEX`), exposing user:pass strings before the `@` host delimiter.
- **Correction**: Implemented `EMBEDDED_CREDENTIALS_REGEX` in `SafeLogger.kt` and expanded sensitive token keywords to include `passwd`, `client_secret`, `session_token`, `jwt`, `ticket`.
- **Regression Test**: `SafeLoggerTest.kt` -> `testRedactEmbeddedUrlCredentials()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-007 (P1): User Metadata and Artwork Overrides Omitted in Queue and Projection Lookups
- **Platform**: Desktop (macOS, Windows, Linux) (`DesktopLibraryRepository.kt`).
- **Subsystem**: Desktop Database & Library Repository.
- **Symptom**: Custom metadata and artwork edits saved via the metadata editor appeared in the primary songs list but were lost when tracks were queued or viewed in Album Details and Artist Details.
- **Root Cause**: `media(ids)`, `findMediaByUriOrFileName`, `albumDetails`, and `artistDetails` queries omitted `LEFT JOIN user_metadata_overrides` and `LEFT JOIN user_artwork_overrides`.
- **Correction**: Standardized all queries on `BASE_SELECT_COLUMNS` and `BASE_JOINS`, ensuring user overrides are consistently applied across all lookups and grouping projections.
- **Regression Test**: `DesktopLibraryIntegrationTest.kt` -> `userMetadataOverridesArePreservedAcrossAllProjectionsAndQueries()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-008 (P2): Raw Percent Hex Shown in Direct URL Streaming Display Titles
- **Platform**: All platforms (`StreamValidator.kt`).
- **Subsystem**: Core Streaming Subsystem.
- **Symptom**: Direct media URLs with percent-encoded characters (such as `%20` or Bengali UTF-8 `%E0%A6%97%E0%A6%BE%E0%A6%A8`) displayed raw hex gibberish in the player title.
- **Root Cause**: `StreamValidator.generateDisplayTitle` did not decode percent-encoded byte sequences into UTF-8 characters.
- **Correction**: Implemented pure Kotlin multiplatform `percentDecode` helper and integrated it into title generation.
- **Regression Test**: `StreamValidatorTest.kt` -> `testPercentDecodedUrlDisplayTitle()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-009 (P2): Synchronized LRC Lyrics Parser Discarded Instrumental Pause Lines
- **Platform**: All platforms (`LrcLyricsParser.kt`).
- **Subsystem**: Core Lyrics Subsystem.
- **Symptom**: During guitar solos or instrumental breaks, the player continued displaying stale lyrics instead of clearing the screen because empty timestamp lines were dropped.
- **Root Cause**: `LrcLyricsParser` required `content.isNotEmpty()` when matching timestamp lines, discarding intentional pause lines (`[01:23.45]`).
- **Correction**: Permitted timestamped lines with empty content to produce `LyricLine(timeMillis, "")`, enabling `findActiveLineIndex` to accurately indicate instrumental interludes.
- **Regression Test**: `LrcLyricsParserTest.kt` -> `testTimestampedInstrumentalBreak()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-010 (P1): PlaybackEngineManager Stuck in Preparing on Preferred Engine Open Failure
- **Platform**: All platforms (`PlaybackEngineManager.kt`).
- **Subsystem**: Core Playback Engine Orchestration.
- **Symptom**: When `openBestEngine` attempted to open media with the preferred candidate (e.g., Media3 or LibVLC) and that engine failed during `open()`, the manager did not fall back to secondary candidates, leaving the player permanently stuck in `PlaybackStatus.Preparing` without attempting alternative engines or transitioning to `PlaybackStatus.Error`.
- **Root Cause**: `openBestEngine` called `switchTo(candidates.first())` without iterating through candidates if `engine.open` failed, and `switchTo` did not have an `onFailure` catch to transition `_state.value` out of `Preparing`.
- **Correction**: Refactored `openBestEngine` to loop through `candidatesFor(queue.current)` until an engine successfully opens media; if all candidates fail, explicitly transitions state to `PlaybackStatus.Error` and releases resources.
- **Regression Test**: `PlaybackEngineManagerTest.kt` -> `fallsBackToNextCandidateWhenPreferredEngineFailsToOpen()`, `marksStateAsErrorWhenAllCandidateEnginesFailToOpen()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-011 (P1): PlaybackQueueController Ghost Playback on Asynchronous Queue Clear
- **Platform**: All platforms (`PlaybackQueueController.kt`).
- **Subsystem**: Playback Queue Management.
- **Symptom**: If a user called `clear()` or removed the active track while `engine.open()` was suspended asynchronously, the engine continued initializing and started playing the removed track after completion (ghost playback).
- **Root Cause**: `openAvailable()` did not verify queue index invariants after `engine.open(item, startPositionMillis)` returned. If `activeIndex < 0` (queue was emptied or active item was deleted), it still returned success and allowed playback to proceed.
- **Correction**: Checked `if (queue.value.activeIndex < 0) { engine.stop(); return Result.failure(...) }` immediately after `engine.open(...)` returns, ensuring any removed media is halted immediately.
- **Regression Test**: `PlaybackQueueControllerTest.kt` -> `stopsEngineAndFailsWhenQueueClearedWhileOpenIsPending()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-012 (P2): Desktop Spacebar Menu Accelerator Blocked Typing in Text Input Fields
- **Platform**: Desktop (macOS, Windows, Linux) (`Main.kt`).
- **Subsystem**: Desktop UI Input & Keyboard Navigation.
- **Symptom**: When typing in search inputs, stream URL fields, or metadata edit forms, pressing Spacebar triggered Play/Pause instead of typing a space character, making it impossible to enter titles or queries with spaces.
- **Root Cause**: The window `MenuBar` registered an unconditional global accelerator `KeyShortcut(Key.Spacebar)`, which preempts all focused child components including text fields.
- **Correction**: Changed MenuBar accelerator to `KeyShortcut(Key.Spacebar, ctrl = true)`, and added root `Window.onKeyEvent` handler for bare Spacebar (as well as `K`, `J`, `L`) that only handles playback commands when text inputs are not consuming the event.
- **Regression Test**: `DesktopUiAccessibilityTest.kt`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-013 (P2): Redundant Announcements and Missing Semantic Roles in UI Components
- **Platform**: All platforms (`VimivoUiComponents.kt`, `DesktopArtwork.kt`, `DesktopVideoControlsPanel.kt`, `DesktopFeedbackDialog.kt`, `VimivoComponents.kt`, `AdvancedVideoPlayer.kt`).
- **Subsystem**: Accessibility & Screen-Reader Semantics.
- **Symptom**: Screen readers (TalkBack, VoiceOver) read empty state / error banner buttons twice (once for button container, once for text); decorative artwork initials were read as words before song titles; video controls buttons lacked explicit roles and descriptions.
- **Root Cause**: Missing `clearAndSetSemantics` on composite button containers; missing `null` content description on decorative thumbnails; missing `ProgressBarRangeInfo` and explicit `Role.Button` semantics.
- **Correction**: Applied `clearAndSetSemantics` to composite buttons, silenced decorative initials, added accessibility semantics to video sliders and delay adjustments.
- **Regression Test**: `DesktopUiAccessibilityTest.kt`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-014 (P1): Metadata Extraction Pipeline Capped at 200 Items Without Incremental Processing Loop
- **Platform**: Desktop (`DesktopMetadataCoordinator.kt`).
- **Subsystem**: Metadata Discovery & Extraction Subsystem.
- **Symptom**: For folders with >200 media files, background metadata extraction ran once for the first 200 candidates and never processed remaining items (e.g., items 201..1000 remained without extracted artist, album, duration, or cover artwork).
- **Root Cause**: `DesktopMetadataCoordinator.start()` invoked `repository.candidates(rootId)` without a continuous loop (`while (isActive)`), and `candidates(rootId)` query was hardcoded with `LIMIT 200`.
- **Correction**: Implemented `while (isActive)` chunked processing loop in `DesktopMetadataCoordinator.kt`, added `cancel(rootId)` cooperative cancellation, and invoked `onMetadataUpdated()` / `repository.invalidate()` between chunks to stream updates to UI.
- **Regression Test**: `IncrementalMediaScannerTest.kt` -> `testMetadataCoordinatorMultiBatchLoopAndCancellation()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-015 (P1): Full Rescans Performed Redundant Database Writes and Redundant Stat Syscalls Without Incremental Change Tracking
- **Platform**: Desktop (`DesktopMediaScanner.kt`).
- **Subsystem**: Media Library Scanner & Change Detection.
- **Symptom**: Rescanning large media libraries repeatedly executed full database updates for all files, re-extracted all attributes via separate syscalls, and did not detect when files were removed from disk (leaving unavailable items marked active).
- **Root Cause**: `DesktopMediaScanner.scan` performed blind `INSERT OR REPLACE` for every discovered file, used `Files.walk` without reusing `BasicFileAttributes`, and lacked diffing against `SELECT uri, size_bytes, modified_ms, available FROM media_items`.
- **Correction**: Implemented `Files.walkFileTree` with direct `BasicFileAttributes` reuse, partitioned candidates into `added`, `updated`, and `unchanged`, and identified deleted files to set `available = 0`.
- **Regression Test**: `IncrementalMediaScannerTest.kt` -> `testIncrementalScanningLifecycleAcrossModifications()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-016 (P2): Artwork Cache Produced Corrupted Black Artifacts on Transparent PNG Cover Art
- **Platform**: Desktop (`DesktopMediaCache.kt`).
- **Subsystem**: Artwork Extraction & Cache Pipeline.
- **Symptom**: When embedded or external cover images were transparent PNGs, downscaling and compressing to JPEG resulted in black background blocks around alpha edges instead of smooth dark theme compositing.
- **Root Cause**: `DesktopMediaCache.scale()` drew transparent image directly onto an RGB BufferedImage without initializing the canvas with a background fill, causing JPEG encoder to render uninitialized alpha regions as pure black.
- **Correction**: Checked `source.colorModel.hasAlpha()`, and rendered a solid background fill with theme-matching `Color(24, 24, 27)` before drawing the scaled image.
- **Regression Test**: `DesktopMediaCacheTest.kt` -> `handlesTransparentPngCompositedOntoDarkBackground()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

### Defect VIM-BUG-017 (P1): Playback Error Banner Lacked Contextual Diagnostic Remediation and Retry Mechanics
- **Platform**: Desktop UI (`Main.kt`, `PlaybackQueueController.kt`).
- **Subsystem**: Playback Recovery & User Journey.
- **Symptom**: When playback failed due to missing native libraries or unreadable media, the desktop UI displayed a generic error text without actionable controls ("Retry", "Skip to Next") or guidance for missing engines, leaving the user stranded.
- **Root Cause**: `Main.kt` rendered an ad-hoc inline text box for playback error; `PlaybackQueueController` lacked an explicit `retryCurrent()` method.
- **Correction**: Integrated `VimivoErrorBanner` with categorized troubleshooting (missing engine, network timeout, format error, I/O error) and actionable "Retry", "Skip to Next", "Dismiss", and "Report Issue" controls; added `retryCurrent(startPositionMillis)` to `PlaybackQueueController`.
- **Regression Test**: `PlaybackQueueControllerTest.kt` -> `retriesCurrentTrackWithoutAdvancingToNext()`. Verified passing.
- **Status**: **VERIFIED FIXED**.

---

## 4. Defect Metrics & Statistics

- **Total Defects Triaged**: 17
- **Total P0 Defects**: 1 (Fixed: 1, Remaining: 0)
- **Total P1 Defects**: 10 (Fixed: 10, Remaining: 0)
- **Total P2 Defects**: 6 (Fixed: 6, Remaining: 0)
- **Total P3 Defects**: 0 open blockers
- **Unresolved Release Blockers**: **0**




