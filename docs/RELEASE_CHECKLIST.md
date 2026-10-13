# Vimivo release checklist

Status: release-candidate preparation, 2026-10-13. Nothing in this document authorizes publication.

## Release blockers before first upload

- Finalize the public brand and application ID. `Vimivo` remains a working name and `dev.vimivo.app` is still configurable. Both become costly or impossible to change after publication; the Play application ID cannot be changed.
- Replace the GitHub privacy-policy URL with a stable, publicly accessible HTTPS page before production rollout.
- Complete LibVLC/LGPL legal review and publish the corresponding-source and relinking materials described in `LICENSE_AUDIT.md`.
- Test the signed release build on real API 26, 33, 35, and 36 devices, including at least one 16 KB page-size device.
- Capture store screenshots from the tested release build. Do not use fake media or copyrighted artwork.

## Identity and versioning

- Store title: **Vimivo: Music & Video Player**.
- Default application ID: `dev.vimivo.app`; override only before first publication with `-Pvimivo.applicationId=...`.
- Version defaults: code `1`, name `1.0.0`. Override with `vimivo.versionCode` and `vimivo.versionName`.
- Increase `versionCode` for every Play upload. Use semantic `major.minor.patch` version names for user-facing releases.
- Verify launcher icon, round icon, adaptive icon, monochrome icon, Android 12+ splash, label, dark/light/AMOLED themes, and Bengali fallback behavior.

## Secure signing

Never commit a keystore, key password, store password, `keystore.properties`, or populated Gradle signing properties.

1. Create the upload key offline and store it in a password manager/encrypted backup.
2. Enroll in Play App Signing and retain the upload-key recovery information.
3. Put these values in user-level `~/.gradle/gradle.properties` or protected CI secrets:

```properties
vimivo.signing.storeFile=/absolute/private/path/vimivo-upload.jks
vimivo.signing.storePassword=REDACTED
vimivo.signing.keyAlias=vimivo-upload
vimivo.signing.keyPassword=REDACTED
```

4. Build with `./gradlew clean :androidApp:bundleRelease -Pvimivo.applicationId=FINAL_ID -Pvimivo.versionCode=N -Pvimivo.versionName=X.Y.Z`.
5. Confirm the build reports a release signing configuration and inspect the AAB with Android Studio's APK Analyzer or `bundletool`.
6. Upload first to an internal testing track, review the generated APKs, pre-launch report, permissions, and device catalog, then promote manually.

Without all four signing properties, Gradle intentionally produces an unsigned release artifact suitable only for verification.

## Technical gates

- `./gradlew detekt lintRelease :shared:testReleaseUnitTest :androidApp:testReleaseUnitTest :androidApp:bundleRelease`
- Run connected instrumentation and Compose tests on a device/emulator matrix.
- Exercise startup, denied/revoked permissions, scan/rescan, audio/video playback, notification controls, audio focus, headset events, PiP, subtitles, track switching, HLS/DASH, offline behavior, backup/restore, and process recreation.
- Test R8 output, missing/corrupt media, repeated engine switching, low-memory eviction, SD-card removal, and route changes.
- Verify native ABIs are only `arm64-v8a`, `armeabi-v7a`, and `x86_64`; validate 16 KB ZIP alignment and real-device loading.
- Review `lint`, Detekt, dependency review, Play pre-launch, Android vitals, and native crash symbols.

## Play Console submission

1. Create the app using the final default language, app/game type, free pricing, and final package ID.
2. Complete app access (no login), ads (none), content rating, target audience, news-app, and Data Safety forms.
3. Add the public privacy-policy URL and verify it without authentication.
4. Supply contact email, category, store listing, icon, feature graphic, phone screenshots, and tablet screenshots if tablet distribution remains enabled.
5. Declare and justify media permissions. Confirm no broad file-management permission is present.
6. Upload the signed AAB to internal testing only; resolve all blocking Play warnings.
7. Validate install/update/uninstall and backup behavior from the Play-delivered build.
8. Create a staged production release only after explicit authorization. Do not auto-publish.

Current Play policy requires phone/tablet submissions to target Android 16/API 36 from 31 August 2026. Vimivo targets API 36. See the [official target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

