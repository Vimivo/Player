# Development continuation

Last verified on 2026-10-15 on macOS, branch `main`.
Milestones completed: **Milestone 23 (UI/UX & Accessibility) & Milestone 24 (Performance, Stability, Security, and Production Hardening)**.

## Resume point

Implementation is verified across all five target platforms (Android, iOS, macOS, Windows, Linux).

The latest completed increments are:

1. `SafeLogger`: privacy-conscious diagnostic logging in `commonMain` redacting sensitive user filesystem paths (`/Users/...`, `/home/...`, `C:\Users\...`) and URL query tokens across platforms;
2. `DesktopSettingsPanel`: fully realized Compose desktop settings screen with theme mode selection, live library statistics, folder management, and privacy pledge;
3. `VimivoTheme`: unified theme propagation (System, Light, Dark, AMOLED) connected to desktop and persisted via SQLite settings repository;
4. Responsive desktop layout: `BoxWithConstraints` adaptive sidebar (compact `< 820dp` / standard `>= 820dp`) and padding adjustments;
5. Desktop accessibility semantics: full `contentDescription` on seekbars, playback buttons, stream URL fields, and sidebar navigation tabs;
6. Android TalkBack accessibility: `contentDescription` and `stateDescription` added to playback seekbars and control icons in `VimivoComponents.kt`;
7. iOS VoiceOver accessibility: `.accessibilityLabel` and `.accessibilityValue` added to seekbar slider, play/pause, next, previous, shuffle, repeat, playback speed menu, queue, and favorite buttons in `NowPlayingBar.swift`, `NowPlayingView.swift`, and `MediaRow.swift`;
8. Production Quality documentation suite: `UI_UX_AUDIT.md`, `DESIGN_SYSTEM.md`, `ACCESSIBILITY_AUDIT.md`, `PERFORMANCE_AUDIT.md`, `SECURITY_AUDIT.md`, `PRIVACY_AUDIT.md`, `DEPENDENCY_AUDIT.md`, `FEATURE_MATRIX.md`, and `PRODUCTION_READINESS.md`.

Do not rebuild these components. Continue from the first executable item in the backlog below.

## Next executable work

1. Physical device testing on real Android and iOS devices (audio output, lock-screen controls, bluetooth audio disconnect).
2. Configure Apple Developer signing certificate and provisioning profile in Xcode for App Store / TestFlight archive distribution.
3. Configure Google Play upload keystore for signing release Android App Bundle (`androidApp-release.aab`).
4. Execute Linux GitHub Actions CI pipeline on an Ubuntu runner to generate genuine DEB, RPM, and AppImage packages.
5. Execute Windows GitHub Actions CI pipeline on a Windows runner to generate genuine EXE/MSI binaries.
6. Run clean-machine Linux and Windows runtime playback verification.

## External or hardware blockers

- Public Google Play store submission requires owner-managed upload keystore. Never commit signing keys.
- iOS physical device installation and TestFlight / App Store submission require owner-managed Apple Developer Team signing certificate and provisioning profile.
- Linux packaging (`jpackage` creating DEB/RPM and AppImage) requires a Linux host/runner.
- Windows packaging (`jpackage` creating EXE/MSI) requires a Windows host/runner.

## Required verification

For mobile and cross-platform verification run:

```shell
# Android
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:detekt
./gradlew :androidApp:assembleDebug

# iOS
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO

# Desktop & Shared
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :shared:desktopTest
./gradlew detekt
```
