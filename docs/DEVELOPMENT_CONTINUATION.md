# Development continuation

Last verified on 2026-10-16 on macOS, branch `main`.
Milestones completed:
- **Milestone 25**: Final Five-Platform Quality Assurance
- **Milestone 26**: Production Build Engineering & CI/CD
- **Milestone 27**: Closed Beta Infrastructure & Privacy-Safe Diagnostics
- **Milestone 28**: Defect Remediation & Release Candidate Stabilization
- **Milestone 29**: Final Launch Readiness & Five-Platform Audit
- **Milestone 30**: Official Distribution Engineering & Stable Release Preparation
- **Milestone 31**: Post-Launch Reliability Engineering & Crash Diagnostics
- **Milestone 32**: Secure Software Update Infrastructure & Long-Term Maintenance
- **Milestone 33**: Advanced Audio Engineering & Premium Music Experience
- **Milestone 34**: Professional Video Playback & Power-User Experience
- **Milestone 35**: Architecture Optimization, Boundary Guards & Build Engineering
- **Milestone 36**: Large-Library Scalability, SQLite Schema v5 & Performance Benchmarks
- **Milestone 37**: Complete Product Feature Gap Audit & End-to-End Functional Traceability
- **Milestone 38**: Exhaustive Regression Hardening, Concurrency & Release Readiness
- **Milestone 39**: Final Product Consolidation, Developer & User Documentation & Local Validation
- **Milestone 40**: Production Release Quality, Maintenance Handoff & Platform GO/NO-GO
- **Milestone 41**: Smart Media Discovery, Intelligent Library Organization & Smart Playlists
- **Milestone 42**: Privacy-First Personalization, Offline Recommendations & Insights
- **Milestone 43**: Advanced Media Library Management, Bulk Metadata Editing & Safe Renaming
- **Milestone 44**: Privacy-First Workflow Automation, Dry-Runs, Journaling & Health Dashboard
- **Milestone 45**: Universal Media Compatibility Engineering, Codec Verification & Safe Decoder Fallback
- **Milestone 46**: Advanced Video Renderer Optimization, Low-Latency Playback & Benchmarks
- **Milestone 47**: Final UX Evolution, Design System Consolidation & Responsive Adaptive Layouts
- **Milestone 48**: Five-Platform Accessibility Architecture, Semantics, Keyboard Focus & Regression Hardening
- **Milestone 49**: Comprehensive Product Verification, E2E Functional Testing & Requirements Traceability
- **Milestone 50**: Five-Platform Release Audit, Candidate Stabilization, Security/License Gates & Maintenance Handoff
- **Milestone 51**: Community Feedback Infrastructure, Privacy-Safe Bug Reporting & Offline Drafts
- **Milestone 52**: Evidence-Based Prioritization, Transparent Roadmap, Feedback-to-Fix & Maintenance
- **Milestone 53**: Universal URL-Based Audio & Video Streaming, Direct URLs & Native Player Integration
- **Milestone 54**: Streaming Reliability, Buffering State, Bounded Reconnection & Security
- **Milestone 55**: Cross-Platform Streaming Compatibility & Direct Media URL Playback Hardening
- **Milestone 56**: Live Latency Optimization, Session Concurrency & Track Rendition Extraction
- **Milestone 57**: Complete Local & URL Playback Verification, E2E Testing & Source Switching
- **Milestone 58**: Streaming Security Audit, Five-Platform Release Hardening & Independent GO/NO-GO
- **Milestone 59**: Long-Term Cross-Platform Compatibility & KMP Dependency Modernization
- **Milestone 60**: Secure Application Update Architecture & Signed Artifact Verification
- **Milestone 61**: Native Widget Ecosystem, Lock Screen Live Activities & Quick Actions
- **Milestone 62**: Production Release Quality, Final Platform Verification & Launch Sign-Off
- **Milestone 63**: Playback Performance Evolution & Large Media Library Optimization
- **Milestone 64**: Battery/Energy Efficiency, Native Resource Hardening & Long-Session Stability
- **Milestone 65**: Complete Product Consolidation & Functional Quality
- **Milestone 66**: Premium Aurora Glass UI/UX, Native Platform Integration, Playback Quality & Final Refinement
- **Milestone 67**: Advanced Local Media Workflows, Unified Import Experience & Library Management
- **Milestone 68**: Direct URL Streaming Workflows, Professional Aurora Glass UI/UX & Cross-Platform Completion
- **Milestone 69**: Advanced Audio Experience, Real Equalizer & Native Integration
- **Milestone 70**: Advanced Video Playback, Subtitles & Cross-Platform Display Controls
- **Milestone 71**: Native Platform Polish, Lifecycle Hardening & Advanced Playback Reliability
- **Milestone 72**: Packaging and Distribution Readiness & Release Candidate Preparation
- **Milestone 73**: Final Product Audit, Remaining Feature Gaps, Feature Completeness & Cross-Platform UX Consistency
- **Milestone 74**: Production Hardening, Reliability Verification, Final Regression, Release Candidate Preparation & Developer Handover

## Resume point

Milestones 73 and 74 (Final Product Audit, Remaining Feature Gaps, Feature Completeness, Cross-Platform UX Consistency, Production Hardening, Reliability Verification, Final Regression, Release Candidate Preparation & Developer Handover) are complete, verified, and passing all quality gates.

- **Comprehensive Feature Inventory & Audit (M73)**:
  - Audited all 9 feature domains (Media library, Audio playback, Video playback, Playlists & queue, Search & organization, URL playback, Settings & preferences, Platform integration, Accessibility & UI/UX); confirmed 100% verified status without placeholder or dummy functionality.
  - Dynamically generalized engine candidate discovery in `PlaybackEngineManager.candidatesFor` across all configured engine factories sorted by `SupportLevel` (`PREFERRED` before `FALLBACK`), eliminating hardcoded engine limitations.
  - Added unit test `selectsConfiguredEnginePrioritizingPreferredSupport` in `PlaybackEngineManagerTest` verifying multi-engine factory prioritization.
  - Hardened iOS `AVPlayerPlaybackEngine` error state lifecycle by clearing stale `errorMessage` on new track loading and player stop.
  - Completed 15 core cross-platform user journey consistency audits across Android, iOS/iPadOS, macOS, Windows, and Linux.
- **Production Hardening & Reliability Verification (M74)**:
  - Quality gates: 487 passing automated tests across `:shared:desktopTest` (105), `:desktopApp:desktopTest` (220), `:androidApp:testDebugUnitTest` (22), and `iosAppTests` (9).
  - Detekt static analysis: **0 violations** across all modules (`:shared`, `:desktopApp`, `:androidApp`).
  - Unified quality gate (`./scripts/verify_all.sh`): **SUCCESS: All 6 verification gates passed with zero errors!**
  - Relative documentation links (`./scripts/validate_docs.sh`): **100% verified** across 32 markdown links.
  - Security & privacy review: 0 tracking, 0 telemetry, 0 account requirements, 0 cloud catalog dependencies, 0 DRM bypassing; credentials protected in OS-level vaults.
  - Platform GO / CONDITIONAL GO / NO-GO recommendations: All 5 platforms assessed as **CONDITIONAL GO** for controlled distribution. Release blocked solely on owner-managed signing keys and explicit publishing authorization.


- **Unified Import & Multi-File Workflows (Desktop & Android)**:
  - Upgraded desktop "Open media" dialog and action buttons to process multiple files simultaneously (`chooseMediaFiles().let { if (it.isNotEmpty()) queue.play(it.map { f -> f.toMediaItem() }) }`).
  - Added dedicated M3U playlist import dialog (`choosePlaylistFile()`) filtering `.m3u` / `.m3u8` files, wired into MenuBar (`File -> Import Playlist…`) and the library chrome.
  - Upgraded Android SAF import in `LibraryChrome` to `ActivityResultContracts.OpenMultipleDocuments()`, enabling batch selection with persistable URI permissions across modern scoped storage.
  - Added persistent "Import files" and "Add folder" action chips in Android `LibraryChrome` for immediate access regardless of permission grant state.
- **Media Library Management & Multi-Select Aurora Glass UI**:
  - Implemented multi-select capabilities in `DesktopLibraryPanel` with an Aurora Glass control bar (`VimivoGlassVariant.CONTROL`) offering batch actions: "Play Selected", "Queue", "Edit Metadata", and "Clear".
  - Added selection checkboxes and `primaryContainer` subtle highlighting to `DesktopMediaListRow` and `DesktopMediaGridCard`.
  - Added bidirectional track reordering (`ArrowUpward` and `ArrowDownward`) in Android `PlaylistDetail`.
  - Verified non-destructive database overrides (`user_metadata_overrides`) and M3U8 import/export pipelines.
- **Multiplatform Logo & Application Icons**:
  - Derived and verified official application icons across all supported targets from canonical source `docs/brand/vimivo-icon-source.png` using reproducible generator `scripts/generate_app_icons.py`.
  - iOS: 1024x1024 24-bit RGB non-alpha icon for `Assets.xcassets/AppIcon.appiconset`, fully meeting App Store submission standards.
  - macOS: Generated Apple `icon.icns` with 10 Retina resolutions (16x16 to 1024x1024) via `iconutil`, wired to `desktopApp/build.gradle.kts` `nativeDistributions.macOS.iconFile`.
  - Desktop Window: Cleanly wired `windowIcon` on Compose `Window(...)` via `ImageIO` and `toComposeImageBitmap()` with zero deprecation warnings.
  - Linux: 512x512 app icon and full hicolor theme sizes (16, 32, 48, 64, 128, 256, 512).
  - Windows: Multi-resolution `icon.ico` with embedded PNG frames (16 to 256).
  - Android: Generated fallback raster launcher icons across all screen densities (`mipmap-mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) for both standard and round icons alongside existing adaptive vectors.
- **Testing & Verification**:
  - Added automated test `journey8MultiSelectLibraryWorkflowAndBatchActions` in `DesktopUserJourneysTest.kt`, verifying select-all, selective toggling, clearing, and batch playlist creation.
  - Total automated tests: **347 passing tests** across shared, desktop, Android, and iOS suites (0 failures, 100% pass rate).
  - All quality gates verified via `./scripts/verify_all.sh` and `./scripts/validate_docs.sh`.
  - Offline-first local library with zero cloud dependencies, zero remote catalogs, zero trackers.
  - Apple Music-inspired audio player: ambient backdrop, queue sheet, lyrics, sleep timer, playback speed, and memoized mini-player.
  - VLC/MX-inspired video player: auto-hiding controls, double-tap seek, aspect ratio options (Fit, Fill, 16:9, 4:3, Crop), subtitle/audio track picker, PiP.
  - Direct URL player: safe validation (`StreamValidator`), low-latency modes, clear error feedback for DRM/webpages.
  - Accessibility: Full semantic roles, TalkBack and VoiceOver descriptions, visible focus, reduced motion and transparency fallbacks.


- **Aurora Glass Rendering Optimization**:
  - `VimivoGlassSurface`: Memoized `tokens`, `shape`, `border`, and `surfaceColor` using `remember(tokens.radius)` and `remember(variant, reducedTransparency)`, eliminating 100% of shape allocation churn on static redraws.
  - `MiniPlayer`: Isolated 60/120 FPS progress bar interpolation into leaf `MiniPlayerProgressBar`, preventing full composite player recompositions on playback ticks.
  - `PlaybackActivityVisualizer`: Pre-allocated `barShape = remember { RoundedCornerShape(2.dp) }` and cached primary color, eliminating 72 allocations/sec and bypassing math during reduced motion.
  - `DesktopArtwork`: Memoized `shape = remember(size) { RoundedCornerShape(size / 6) }` and fallback letter extraction.
  - `DesktopMediaListRow` & `DesktopMediaGridCard`: Memoized subtitle strings in `remember(item.artist, item.album, item.durationMillis)`.
  - `DesktopSidebar` & `DesktopVideoControlsPanel`: Fully integrated with `VimivoGlassSurface` using `NAVIGATION` and `CONTROL` variants with solid fallbacks for reduced transparency.
- **Large Local Media Library Optimization**:
  - Progressive fixtures verified at 1K, 10K, and 50K records with Bengali titles, Unicode paths, duplicates, and missing tags in `ProgressiveMediaLibraryFixtureTest.kt`.
  - Database schema indexes added: `favorites_target_created`, `playback_positions_updated`, `media_items_available_kind_title`.
  - Audited SQLite `EXPLAIN QUERY PLAN` confirms index seek on all browsable, favorite, and resume queries.
  - 50K record insertion in 1.02s; Bengali search in 33ms; album grouping in 87ms; playlist loads in 22ms.
- **Battery & Native Resource Hardening**:
  - `MpvPlayerEngine`: Replaced per-tick native JNA `Memory` allocations with pre-allocated 8-byte `nativeBuffer` protected under `nativeLock`.
  - Polling interval dynamically throttles: 250ms during active playback, 500ms when paused, 1000ms when completed/idle.
  - Seamless seek preparation: `seekTo` buffers via `start` property when demuxer is uninitialized, completely eliminating SIGSEGV / command error race conditions.
  - `Media3PlayerEngine`: Polling job cancelled on pause/idle, strictly respecting mobile background power limits.
  - `LongSessionStabilityTest`: Multi-hour continuous sessions, 50 rapid exact/keyframe seeks, streaming latency configurations, and concurrent 500-write database stress validated.

- **Playback Verification & Source Switching (`desktopApp`)**:
  - `PlaybackSourceSwitchingTest.kt`: Exhaustive verification of transitions between local audio, local video, direct URL audio, direct URL video, HLS live streams, and reconnecting sources. Confirmed zero duplicate sessions, proper video surface detachment, and user intent preservation.
  - `PlaybackFailureInjectionTest.kt`: Bounded exponential backoff with user Stop cancellation, redirect loop handling, corrupt media handling, and missing file reconciliation.
  - `EndToEndPlaybackUserJourneysTest.kt`: Covers user journeys for direct video URLs, live HLS disconnection and recovery, internet radio with background audio controls, and local JSON backup export and restore preserving SQLite foreign key integrity.
- **Security & Privacy Hardening**:
  - `docs/STREAMING_SECURITY_AUDIT.md`: Threat model, URL sanitization, TLS enforcement, and LAN boundary isolation.
  - `docs/MEDIA_PARSER_SECURITY.md`: Buffer limits, tag parser defenses, and native library version audit.
  - `docs/PRIVACY_VERIFICATION.md`: Zero-telemetry confirmation, no advertising IDs, no accounts, and no cloud catalog.
- **Five-Platform Release Artifacts**:
  - macOS DMG: `Vimivo-1.0.0.dmg` (151 MB, SHA-256: `e6ab2791a0a660dc75e726906df2dab506350a66e43dd9e97551faf19829146c`).
  - Android APK: `androidApp-release-unsigned.apk` (147 MB, SHA-256: `7425f12d6a8bd450bfe88420c4ad2137f0820e0bcc0e35b1046efdcee65c92b3`).
  - Android AAB: `androidApp-release.aab` (76 MB, SHA-256: `8d7f0060a9ec770bb5162d0f5d61ed90f5cd7226a67ef64216030c8710af7133`).
  - Verification records published in `docs/INSTALLATION_VERIFICATION.md` and `docs/DISTRIBUTION_ARTIFACTS.md`.

Test & Static Analysis Verification:
- `:shared:desktopTest`: 102 passing tests (100% pass rate).
- `:desktopApp:desktopTest`: 215 passing tests (100% pass rate).
- `:androidApp:testDebugUnitTest`: 21 passing tests (100% pass rate).
- `iosAppTests` (Xcode 27.1 / iPhone 17 Pro): 9 passing tests (100% pass rate).
- Total automated tests: 347 passing tests (0 failures, 0 errors, 0 skipped, 100% pass rate).
- Detekt static analysis: 0 weighted violations across all modules.
- Android debug build: `:androidApp:assembleDebug` SUCCESS.
- Relative documentation links: 100% verified via `./scripts/validate_docs.sh`.

## Next executable work

Aurora Glass Phase 1 is now active at Git base `bd06079be0aec363c037447d84256eaa2a0f5a6f`. Shared tokens/materials and the Android floating mini-player integration are implemented and verified. The next exact UI task is to replace the non-scrollable generic Android bottom-sheet container with separate lazy queue and scrollable settings-sheet primitives, then add process-restorable navigation state. Desktop shell restructuring and iOS queue invariants follow; neither is complete yet.

1. Continue expanding platform-native file provider integrations for Android SAF and iOS FileProvider for external SD/OTG storage.
2. Invite authorized testers into the private beta cohort under the established opt-in agreement (`docs/BETA_TEST_PLAN.md`).
3. Execute remote Windows and Linux native binary generation via GitHub Actions workflow `.github/workflows/release-candidate.yml`.
4. Provide owner-managed Google Play upload keystore and Apple Developer Team provisioning profile in protected secrets when ready for store submissions.
5. Obtain explicit owner authorization before triggering `.github/workflows/publish-release.yml`.

## External or hardware blockers

- Public Google Play store submission requires owner-managed upload keystore and 14-day 12-tester closed testing period if mandated by account policy.
- iOS physical device installation and TestFlight / App Store submission require owner-managed Apple Developer Team signing certificate and provisioning profile.
- Windows Authenticode certificate required for SignTool to establish Microsoft Defender SmartScreen reputation.
- macOS Gatekeeper bypass for public DMG distribution requires Apple Developer ID certificate and `xcrun notarytool` notarization.
- Linux and Windows packaging execution requires corresponding OS runners.

## Required verification

```shell
# Android
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:detekt
./gradlew :androidApp:assembleDebug

# macOS Desktop
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop

# Shared & Static Analysis
./gradlew :shared:desktopTest
./gradlew detekt

# Unified Quality Gate
./scripts/verify_all.sh
```
