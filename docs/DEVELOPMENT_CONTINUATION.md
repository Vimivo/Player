# Development continuation

Last verified on 2026-10-16 on macOS, branch `main`.
Milestones completed:
- **Milestone 25**: Final Five-Platform Quality Assurance
- **Milestone 26**: Production Build Engineering & CI/CD
- **Milestone 27**: Closed Beta Infrastructure & Privacy-Safe Diagnostics
- **Milestone 28**: Defect Remediation & Release Candidate Stabilization

## Resume point

Implementation, quality gates, and closed-beta infrastructure are verified across all five target platforms (Android, iOS, macOS, Windows, Linux).

The latest completed increments are:

1. **Privacy-Safe Crash & Runtime Diagnostics**:
   - `shared/src/commonMain/kotlin/dev/vimivo/core/diagnostics/PrivacySafeDiagnostics.kt`: Thread-safe bounded in-memory buffer (`DiagnosticBuffer`) and markdown/JSON report generator (`DiagnosticReport`) that redacts usernames (`~/user/...`), URLs, and credentials (`token=[REDACTED]`).
   - `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/diagnostics/DesktopDiagnosticExporter.kt`: Desktop diagnostic export manager with security checks blocking writes to protected root system directories (`/System`, `/etc`, `/private/etc`, `C:\Windows`).
   - `shared/src/commonMain/kotlin/dev/vimivo/core/util/SafeLogger.kt`: Extended with key-value credential pair sanitization (`SENSITIVE_KEY_VALUE_REGEX`).
   - Unit tests: `PrivacySafeDiagnosticsTest.kt` (8 tests) and `DesktopDiagnosticExporterTest.kt` (3 tests) passing 100%.

2. **Closed Beta Feedback Infrastructure**:
   - `.github/ISSUE_TEMPLATE/bug_report.yml`: Structured bug report form with platform dropdown, severity (P0-P3), reproducibility, format details, and privacy check.
   - `.github/ISSUE_TEMPLATE/feature_request.yml`: Separate feature suggestions form.
   - `.github/ISSUE_TEMPLATE/config.yml`: Blank issues disabled, linking directly to documentation.

3. **Beta Testing & Defect Remediation Documentation Suite**:
   - `docs/BETA_TEST_PLAN.md`: Cohort design, hardware coverage matrix, and 3-level readiness gates.
   - `docs/BETA_TESTER_GUIDE.md`: Professional onboarding guide, SHA-256 verification, safe installation, and data safety guarantee.
   - `docs/BETA_SCENARIOS.md`: Repeatable scenarios covering first launch, music, video, playlists, queue, personal data, and fault tolerance.
   - `docs/BETA_COMPATIBILITY_MATRIX.md`: Real-world audio, video, codec, container, and subtitle compatibility across all five platform backends.
   - `docs/BETA_FEEDBACK_PROCESS.md`: Triage lifecycle, deduplication criteria, and 14-step Root-Cause Analysis protocol.
   - `docs/BETA_PRIVACY.md`: Tester data safety pledge (zero telemetry, zero trackers, local-only media, unconsented diagnostic prevention).
   - `docs/BETA_DIAGNOSTICS.md`: Diagnostic subsystem technical architecture and redaction guide.
   - `docs/BUG_TRIAGE.md`: Investigated defects register (VIM-BUG-001 through 004, 0 unresolved P0/P1 issues).
   - `docs/UPGRADE_VERIFICATION.md`: Beta-to-RC upgrade testing, non-destructive SQLite migrations (v0->v4), and rollback limitations.
   - `docs/RC_GO_NO_GO.md`: Independent platform release decisions (Gates A through M) recommending CONDITIONAL GO for closed beta.
   - `docs/KNOWN_ISSUES.md`: Technical limitations register (Gatekeeper, SmartScreen, Wayland, and Google Play policies).

4. **Production Build Engineering & Artifacts**:
   - Android: `androidApp-release-unsigned.apk` (147,577,927 bytes) & `androidApp-release.aab` (76,079,193 bytes).
   - macOS: `Vimivo-1.0.0.dmg` (150,423,483 bytes) and standalone `Vimivo.app`.
   - CI workflows: `.github/workflows/macos.yml`, `.github/workflows/ios.yml`, `.github/workflows/android.yml`, `.github/workflows/shared.yml`, `.github/workflows/release-candidate.yml`.
   - Artifact checksums in `docs/SHA256SUMS.txt`, CycloneDX SBOM in `docs/sbom.json`, and `docs/release-manifest.json`.

5. **Test & Static Analysis Verification**:
   - `:shared:desktopTest`: 23 passing tests.
   - `:desktopApp:desktopTest`: 82 passing tests.
   - `:androidApp:testDebugUnitTest`: 20 passing tests.
   - Total automated tests: 125 tests passing (0 failures).
   - Detekt static analysis: 0 violations across all modules.

## Next executable work

1. Invite authorized testers into the private beta cohort under the established opt-in agreement.
2. Evaluate genuine field feedback received from real beta testers (Status currently: **AWAITING AUTHORIZED TESTERS**).
3. Execute `.github/workflows/release-candidate.yml` on an authorized tag/dispatch to build Windows (EXE/MSI) and Linux (DEB/RPM/AppImage) binaries on native CI runners.
4. When authorized by owner, supply Apple Developer ID and Google Play upload credentials to sign production packages.

## External or hardware blockers

- Public Google Play store submission requires owner-managed upload keystore. Never commit signing keys.
- iOS physical device installation and TestFlight / App Store submission require owner-managed Apple Developer Team signing certificate and provisioning profile.
- Windows Authenticode certificate required for SignTool to establish SmartScreen reputation.
- macOS Gatekeeper bypass for public DMG distribution requires Apple Developer ID certificate and `xcrun notarytool` notarization.
- Linux and Windows packaging execution requires corresponding OS runners.

## Required verification

```shell
# Android
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:detekt
./gradlew :androidApp:assembleRelease :androidApp:bundleRelease

# macOS Desktop
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:packageDmg

# Shared & Static Analysis
./gradlew :shared:desktopTest
./gradlew detekt
```
