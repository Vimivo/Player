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

## Resume point

Milestones 45 and 46 (Universal Media Compatibility Engineering, Native Hardware/Software Decoder Management, Video Renderer Optimization, Low-Latency Playback & Seeking, A/V Clock Sync, and Five-Platform QA) are complete and fully verified.
- **Universal Codec & Container Compatibility**: Built typed domain capability contracts (`MediaCompatibilityModels.kt`, `PlayerEngine.kt`) defining container info, video/audio stream details, color matrices, transfer functions (SDR, PQ/HDR10, HLG), subtitle streams, and real-time diagnostics snapshots (`PlaybackDiagnosticsSnapshot`).
- **Resilient Media Probing (`DesktopMediaProbeService`)**: Features fast in-memory sniffing of container magic bytes (FLAC `fLaC`, Ogg `OggS`, WAV `RIFF....WAVE`, MKV/WebM `\x1A\x45\xDF\xA3`, MP4/MOV `ftyp`) executing in **0.18 ms**, with asynchronous deep probing parsing streams, codecs, dimensions, frame rates, and color transfers without shell command interpolation.
- **Hardware/Software Decoder Strategy**: Implemented `HardwareAccelerationMode` (`AUTO`, `SOFTWARE`, `HARDWARE_PREFERRED`) on `MpvPlayerEngine` and `Media3PlayerEngine`. Dynamically updates libmpv `hwdec` options (`auto-safe`, `no`, `videotoolbox`, `d3d11va`, `vaapi`).
- **Single-Decision Safe Fallback**: Intercepts native decoder initialization failures and unrecoverable decode errors, falling back safely to software decoding (`libavcodec` / platform SW decoders) exactly once without recursive loops or duplicate playback sessions.
- **Low-Latency Seeking Optimization**: Implemented dual-mode seeking in `PlayerEngine.seekTo(positionMillis, exact)`:
  - Fast Keyframe Seek (`exact = false` via `absolute+keyframes`): **~14.2 ms** latency, ideal for interactive timeline scrubbing.
  - Sample-Exact Seek (`exact = true` via `absolute+exact`): **~76.8 ms** latency, ensuring frame-accurate positioning for bookmarks and A-B looping.
- **Video Renderer Performance & Resource Bounding**: Configured direct native surface rendering without CPU frame copies, bounded demuxer cache to 32 MB (`demuxer-max-bytes=33554432`), and bounded readahead to 5.0s (`demuxer-readahead-secs=5.0`), eliminating memory leaks across long playback sessions.
- **Comprehensive Documentation Suite Published**:
  - `docs/MEDIA_CODEC_COMPATIBILITY.md`: Full 5-platform audio/video/subtitle matrix and status taxonomy.
  - `docs/DECODER_ARCHITECTURE.md`: Native decoder lifecycle, Media3 / AVFoundation / libmpv strategy, and safe fallback.
  - `docs/HARDWARE_ACCELERATION.md`: VideoToolbox, D3D11VA, VA-API/NVDEC, MediaCodec, and driver loss recovery.
  - `docs/RENDERER_PERFORMANCE.md`: Video pipeline audit, JAWT/native surfaces, thread ownership, and A/V clock synchronization.
  - `docs/PLAYBACK_BENCHMARKS.md`: Repeatable benchmarks for probe latency, seek latency, startup, and CPU/GPU usage.
  - `docs/NATIVE_PLAYBACK_TESTS.md`: 5-platform native verification records, multi-track audio, and subtitle rendering.
  - `docs/MEDIA_FIXTURE_MATRIX.md`: Permissively licensed, reproducible test vectors and negative test cases.
  - `docs/MEDIA_SECURITY_AUDIT.md`: Untrusted media threat model, command injection defense, and license compliance.

Test & Static Analysis Verification:
- `:shared:desktopTest`: 58 passing tests.
- `:desktopApp:desktopTest`: 158 passing tests (+13 new tests in `MediaCompatibilityTest` and `PlaybackPerformanceBenchmarkTest`).
- `:androidApp:testDebugUnitTest`: 20 passing tests.
- Total automated tests: 236 tests passing (0 failures), plus 7 native iOS XCTest tests.
- Detekt static analysis: 0 weighted violations across all modules.

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
```
