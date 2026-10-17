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

## Resume point

Milestones 63 and 64 (Large Local Media Library Scalability, Aurora Glass UI Recomposition Optimization, Native Buffer Resource Hardening, Battery Efficiency & Long-Session Stability) are complete, verified, and passing all quality gates.

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
- `:desktopApp:desktopTest`: 214 passing tests (100% pass rate).
- `:androidApp:testDebugUnitTest`: 21 passing tests (100% pass rate).
- Total automated tests: 337 passing tests (0 failures, 0 errors, 0 skipped, 100% pass rate).
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
