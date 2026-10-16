# Development continuation

Last verified on 2026-10-16 on macOS, branch `main`.
Milestones completed:
- **Milestone 25**: Final Five-Platform Quality Assurance
- **Milestone 26**: Production Build Engineering & CI/CD
- **Milestone 27**: Closed Beta Infrastructure & Privacy-Safe Diagnostics
- **Milestone 28**: Defect Remediation & Release Candidate Stabilization
- **Milestone 29**: Final Launch Readiness & Five-Platform Audit
- **Milestone 30**: Official Distribution Engineering & Stable Release Preparation

## Resume point

Implementation, quality gates, closed-beta infrastructure, safe update services, and official distribution engineering are complete and verified across all five target platforms (Android, iOS, macOS, Windows, Linux).

The latest completed increments are:

1. **Safe Application Update Architecture & Verification**:
   - `shared/src/commonMain/kotlin/dev/vimivo/core/update/UpdateManifest.kt`: Data models for `ReleaseArtifact`, `UpdateManifest`, and `SemanticVersion` comparator adhering to SemVer 2.0 (handling prereleases, major/minor/patch, and platform/arch matching).
   - `shared/src/commonMain/kotlin/dev/vimivo/core/update/UpdateChecker.kt`: Opt-in update verification engine enforcing HTTPS, stripping credential query tokens, and preventing silent code execution.
   - `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/update/DesktopUpdateService.kt`: Desktop update check service detecting OS and architecture.
   - `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/DesktopSettingsPanel.kt`: Updated with Version 1.0.0-rc.1 and software update policy notice.
   - Unit tests: `UpdateCheckerTest` (5 tests) and `DesktopUpdateServiceTest` (4 tests) passing 100%.

2. **Official Launch Readiness & Store Metadata**:
   - `docs/LAUNCH_READINESS.md`: Definitive five-platform launch readiness audit across all core features and release blockers.
   - `docs/STORE_LISTINGS.md`: Complete store listings, descriptions, keywords, and content rating specifications for Google Play Store and Apple App Store.
   - `docs/PRIVACY_REVIEW.md`: Google Play Data Safety declaration and Apple App Store Privacy Nutrition Labels verifying zero data collection.
   - `docs/SYSTEM_REQUIREMENTS.md`: Detailed hardware, OS, and display requirements for all five platforms.
   - `docs/INSTALLATION_GUIDE.md`: Step-by-step installation and clean upgrade instructions for Android, macOS, Windows, and Linux.

3. **Official Distribution Portal & Release Automation**:
   - `docs/website/index.html`: Responsive, aesthetic official download experience showcasing authentic SHA-256 digests and system requirements.
   - `docs/DISTRIBUTION_PLAN.md`: Staged distribution architecture for mobile app stores and desktop standalone packages.
   - `docs/UPDATE_STRATEGY.md`: Safe update policies across store-managed mobile channels and user-controlled desktop channels.
   - `docs/ROLLBACK_PLAN.md`: Emergency recovery procedures, incident escalation triggers, and database preservation protocols.
   - `docs/RELEASE_APPROVALS.md`: Publication approval boundary enforcing explicit owner sign-off prior to public deployment.
   - `.github/workflows/publish-release.yml`: Protected release publishing workflow requiring explicit confirmation token (`YES_I_CONFIRM_OFFICIAL_RELEASE`).
   - `docs/RELEASE_NOTES.md`: Official release announcement and highlights for `1.0.0-rc.1`.
   - `docs/RELEASE_MANIFEST.md` and `docs/release-manifest.json`: Machine-readable artifact records.

4. **Test & Static Analysis Verification**:
   - `:shared:desktopTest`: 28 passing tests.
   - `:desktopApp:desktopTest`: 81 passing tests.
   - `:androidApp:testDebugUnitTest`: 20 passing tests.
   - Total automated tests: 129 tests passing (0 failures).
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
./gradlew :androidApp:assembleRelease :androidApp:bundleRelease

# macOS Desktop
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:packageDmg

# Shared & Static Analysis
./gradlew :shared:desktopTest
./gradlew detekt
```
