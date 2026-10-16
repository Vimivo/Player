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

## Resume point

Milestones 53 and 54 (Universal User-Provided URL-Based Audio & Video Streaming, Cross-Platform Network Media Playback, Direct Media URL Support, HLS / DASH / RTSP Capability Engineering, Live Video and Internet Radio Streaming, Native Streaming Player Integration, Adaptive Quality and Buffering, Stream Reconnection and Error Recovery, Secure URL Validation, and Streaming Performance QA) are complete and verified.
- **Shared Streaming Domain Subsystem (`dev.vimivo.core.streaming`)**:
  - `StreamModels.kt`: `StreamProtocol`, `StreamType` (LIVE/VOD), `StreamConnectionState`, `StreamFailureReason`, `StreamValidationResult`, `StreamRequest`, `StreamDiagnostics`.
  - `StreamValidator.kt`: Validates schemes (`http`, `https`, `rtsp`, `rtsps`), rejects embedded credentials (`@`), rejects local/dangerous schemes (`file`, `data`, etc.), rejects portal webpages (`youtube.com`, `vimeo.com`, `tiktok.com`, `facebook.com`, `.html`, etc.) with user-actionable explanations, and infers protocol, display title, and mime type.
  - `StreamReconnectionPolicy.kt`: Bounded exponential backoff policy (max 3 retries, 1s/2s/4s delays capped at 6s), immediate abort on user stop/switch, blocks retry on permanent HTTP/network errors.
  - `StreamValidatorTest.kt`: Comprehensive test suite verifying VOD, Live, HLS, DASH, RTSP, radio, webpage rejections, and reconnection backoff.
- **Desktop Implementation (`desktopApp`)**:
  - `DesktopOpenStreamDialog`: Compose Desktop dialog with live validation, protocol badges (`[HLS]`, `[DASH]`, `[RTSP]`, `[LIVE]`), paste, recent streams list, "Play Now", and "Add to Queue".
  - `DesktopNetworkStreamPanel`: Dedicated network stream management surface with protocol capabilities, privacy notice, and recent streams history.
  - `DesktopStreamHistoryRepository`: Privacy-safe recent streams JSON storage with sensitive query parameter redaction.
  - `MpvPlayerEngine`: Configured with `network-timeout=15`, `tls-verify=yes`, `user-agent`, real buffering status via `paused-for-cache`, and forward cache calculation via `demuxer-cache-time`.
  - `DesktopPlayer` / `Main.kt`: Integrated File menu shortcut (`Cmd+U` / `Ctrl+U`), inline stream input, Now Playing `[LIVE]` badge, and disabled live seekbar.
- **Android Implementation (`androidApp`)**:
  - `NetworkViewModel.kt`: Updated `validateNetworkStreamUrl` and `toMediaItem` to use shared `StreamValidator`, protecting against invalid webpage URLs and embedded credentials.
  - Media3 ExoPlayer integration: HLS (`media3-exoplayer-hls`), DASH (`media3-exoplayer-dash`), and RTSP (`media3-exoplayer-rtsp`).
- **Documentation Suite Published**:
  - `docs/URL_STREAMING_ARCHITECTURE.md`: Architecture, data flows, and single-player engine reuse.
  - `docs/STREAMING_PROTOCOL_SUPPORT.md`: Protocol matrix across Android, macOS, Windows, Linux, and iOS.
  - `docs/STREAM_URL_SECURITY.md`: Threat model, credential protection, and HTTP status classifications.
  - `docs/STREAMING_PLAYER_UX.md`: Player entry points, modal dialog, and live vs VOD semantics.
  - `docs/STREAMING_NETWORK_RECOVERY.md`: Bounded backoff policy and error recovery matrix.
  - `docs/STREAMING_PERFORMANCE.md`: Buffer metrics, memory thresholds, and benchmark measurements.
  - `docs/NATIVE_STREAMING_TESTS.md`: Native test matrix and automated test coverage.
  - `docs/STREAMING_PRIVACY.md`: Zero-cloud guarantee, telemetry avoidance, and local storage rules.
  - `docs/STREAMING_LIMITATIONS.md`: Honest scope, webpage vs media distinctions, and DRM boundaries.
  - `docs/STREAMING_COMPATIBILITY_MATRIX.md`: Comprehensive 5-platform capability matrix for Milestones 55 & 56.
  - `docs/STREAM_SESSION_LIFECYCLE.md`: Concurrency isolation, session tokens, and user intent preservation.
  - `docs/STREAM_RECOVERY.md`: Network failure classification, backoff policies, and CRLF header injection defense.
  - `docs/STREAMING_TEST_FIXTURES.md`: In-process deterministic test stream server and mock endpoints.
  - `docs/STREAMING_LATENCY.md`: Live streaming latency modes, readahead tuning, and buffer optimization.
  - `docs/STREAMING_RELEASE_READINESS.md`: Release readiness verdicts and quality gate verification.

Milestones 55 & 56 Implementation & Verification:
- **Live Latency Optimization**: Added `LiveLatencyMode` (`BALANCED`, `LOW_LATENCY`, `STABLE_PLAYBACK`) dynamically tuning `demuxer-readahead-secs` and `demuxer-max-bytes` in `MpvPlayerEngine` without surface recreation.
- **Session Lifecycle Concurrency Isolation**: Added atomic `sessionGeneration` counter preventing stale callbacks, delayed errors, or race conditions across rapid source switching.
- **User Intent Preservation**: User explicit `Stop` cancels reconnection and resets status to `PlaybackStatus.Idle`; user explicit `Pause` sets `userPaused` ensuring paused status persists across buffer completions.
- **Rich Track Rendition Extraction**: Enriched `TrackInfo` with demuxer bitrate, channel count, sample rate, width, height, and FPS.
- **Deterministic Test Infrastructure**: Built `TestStreamServer` serving direct audio with byte-ranges (206), HLS VOD and live sliding window manifests, DASH manifests, and HTTP fault codes (401, 403, 404, 408, 429, 500, redirects).
- **Automated Test Coverage**: Added `StreamSessionLifecycleTest` and enhanced `StreamValidatorTest` verifying fault classification, header injection rejection, and session state transitions.

Test & Static Analysis Verification:
- `:shared:desktopTest`: 114 passing tests (100% pass rate).
- `:desktopApp:desktopTest`: 178 passing tests (100% pass rate).
- `:androidApp:testDebugUnitTest`: 40 passing tests (100% pass rate).
- Total automated tests: 332 passing tests (0 failures, 0 errors, 0 skipped, 100% pass rate).
- Detekt static analysis: 0 weighted violations across all modules.
- Android debug build: `:androidApp:assembleDebug` SUCCESS.

## Next executable work

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
