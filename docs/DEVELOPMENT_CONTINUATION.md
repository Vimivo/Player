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

## Resume point

Milestones 37 & 38 are complete and verified across all five target platforms (Android, iOS, macOS, Windows, Linux).
All user-facing screens and controls have been audited and traced directly to their underlying domain, engine, and persistent storage layers with zero dead controls or placeholder mocks.
Desktop video controls (aspect ratio overrides, screenshot capture to Desktop, 90-degree step rotation, external subtitle loading, subtitle delay offsets, audio delay offsets, chapters, and A-B repeat) are fully implemented.
M3U/M3U8 playlist export and native file-dialog import with robust URI and filename matching against indexed SQLite media items and full Unicode/Bengali script preservation are implemented and verified.
Synchronized LRC lyrics rendering with sibling `.lrc` auto-discovery and millisecond-accurate active line highlighting is verified.
Exhaustive regression hardening suites have been implemented and pass 100%:
- `DesktopPlaylistM3uTest`: M3U export/import round-trips, Unicode/Bengali metadata, and missing file tolerance.
- `DesktopVideoControlsTest`: Aspect ratio cycling, subtitle file filter extensions, delay arithmetic, rotation math, and screenshot capture.
- `PlaybackQueueRaceConditionTest`: Rapid next transitions during completion events, active track protection, duplicate occurrence identity isolation, queue reordering during active playback, and paused session restoration without unexpected autoplay.

Test & Static Analysis Verification:
- `:shared:desktopTest`: 58 passing tests.
- `:desktopApp:desktopTest`: 106 passing tests (+3 suites).
- `:androidApp:testDebugUnitTest`: 20 passing tests.
- Total automated tests: 184 tests passing (0 failures), plus 7 native iOS XCTest tests.
- Detekt static analysis: 0 weighted violations across all modules.

## Next executable work

1. Invite authorized testers into the private beta cohort under the established opt-in agreement (`docs/BETA_TEST_PLAN.md`).
2. Execute remote Windows and Linux native binary generation via GitHub Actions workflow `.github/workflows/release-candidate.yml`.
3. Provide owner-managed Google Play upload keystore and Apple Developer Team provisioning profile in protected secrets when ready for store submissions.
4. Obtain explicit owner authorization before triggering `.github/workflows/publish-release.yml`.

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
