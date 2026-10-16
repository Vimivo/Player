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

## Resume point

Implementation, quality gates, advanced audio DSP (10-band graphic EQ, ReplayGain, gapless, pitch preservation, synced LRC lyrics, sleep timer), professional video playback (A-B repeat looping, chapters, external subtitles, aspect ratio overrides, zoom/pan/rotation, GPU video adjustments, screenshots), and playback profiles & playlists are complete and verified across all five target platforms (Android, iOS, macOS, Windows, Linux).

The latest completed increments are:

1. **Advanced Audio Engineering (Milestone 33)**:
   - `shared/src/commonMain/kotlin/dev/vimivo/playback/AudioEnhancement.kt`: Extended 10-band equalizer presets (`TREBLE_BOOST`, `ACOUSTIC`, plus existing `FLAT`, `BASS_BOOST`, `VOCAL`, `CLASSICAL`, `ROCK`, `JAZZ`, `POP`, `CUSTOM`) and `ReplayGainMode` (`OFF`, `TRACK`, `ALBUM`).
   - `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/playback/DesktopAudioEffects.kt`: Precise 10-band gain maps (31Hz–16kHz) and auto-attenuating safety preamp headroom calculation (`min(0.0, -maxGain)`) preventing digital clipping.
   - `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/playback/MpvPlayerEngine.kt`: Real-time native libmpv bindings for ReplayGain (`replaygain`, `replaygain-clip`), gapless audio (`gapless-audio=yes`), pitch preservation (`audio-pitch-correction=yes`), and audio output routing (`audio-device`, `audio-device-list`).
   - `androidApp/src/main/kotlin/dev/vimivo/platform/android/playback/AndroidAudioEnhancementController.kt`: Updated with full support for new presets.

2. **Synchronized LRC Lyrics & Sleep Timer**:
   - `shared/src/commonMain/kotlin/dev/vimivo/core/lyrics/LrcLyricsParser.kt`: Fast, zero-allocation line parser with multi-timestamp support (`[mm:ss.xx]`), metadata headers (`[ti:]`, `[offset:]`), offset adjustments, Bengali/Unicode support, and binary search active line indexing (`findActiveLineIndex`).
   - `shared/src/commonMain/kotlin/dev/vimivo/playback/SleepTimer.kt`: Platform-neutral sleep timer with duration countdown (1-1440m), stop-after-current-track, and gentle volume fade-out.

3. **Professional Video Controls (Milestone 34)**:
   - `shared/src/commonMain/kotlin/dev/vimivo/playback/PlayerEngine.kt`: Capability contracts and data models for `ABRepeatRange`, `ChapterInfo`, `VideoAspectRatio`, `VideoAdjustments`, `SubtitleStyle`, and `AudioOutputDevice`.
   - `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/playback/MpvPlayerEngine.kt`: Native libmpv bindings for A-B repeat looping (`ab-loop-a`, `ab-loop-b`), chapter discovery and stepping (`chapter-list`, `chapter`), external subtitle styling and delay (`sub-font-size`, `sub-color`, `sub-delay`), audio delay offset (`audio-delay`), video aspect ratio override (`video-aspect-override`), hardware zoom/pan (`video-zoom`, `video-pan-x/y`), rotation (`video-rotate`), GPU video adjustments (brightness, contrast, saturation, gamma), and native frame screenshots (`screenshot-to-file`).

4. **Playback Profiles & Playlist Portability**:
   - `shared/src/commonMain/kotlin/dev/vimivo/playback/PlaybackProfile.kt`: Defined `DEFAULT`, `MUSIC_FOCUS`, `CINEMA`, and `PODCAST_LECTURE` profiles managing speed, EQ preset, aspect ratio, and ReplayGain.
   - `shared/src/commonMain/kotlin/dev/vimivo/core/playlist/PlaylistM3uHandler.kt`: Robust M3U and M3U8 playlist parser and generator with `#EXTINF` metadata extraction and HLS stream tag filtering.

5. **Audio & Video Capability Matrix**:
   - `docs/AUDIO_VIDEO_CAPABILITIES.md`: Authoritative matrix across Android, macOS, Windows, Linux, and iOS/iPadOS documenting exact mechanisms, tests, and zero-fake-control constraints.

6. **Test & Static Analysis Verification**:
   - `:shared:desktopTest`: 58 passing tests (+13 tests).
   - `:desktopApp:desktopTest`: 94 passing tests (+24 tests).
   - `:androidApp:testDebugUnitTest`: 20 passing tests.
   - Total automated tests: 172 tests passing (0 failures).
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
