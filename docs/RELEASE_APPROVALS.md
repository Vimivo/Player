# Vimivo Publication Approval Boundary & Sign-Off Register

## 1. Governance & Boundary Policy

In strict accordance with project release engineering rules:
**PREPARATION IS AUTHORIZED. PUBLIC PUBLICATION IS NOT AUTOMATICALLY AUTHORIZED.**

All automated pipelines, CI/CD workflows, build scripts, and local developer tasks are permitted to compile code, run regression tests, generate release artifacts, produce checksums, and draft documentation.
However, **no build, tag, or store submission may be deployed publicly without explicit, recorded owner sign-off**.

---

## 2. Actions Requiring Explicit Owner Authorization

The following actions strictly require explicit user/owner sign-off:
- [ ] Pushing public Git release tags (e.g. `git tag -a v1.0.0`).
- [ ] Creating public GitHub Releases on `https://github.com/Vimivo/Player`.
- [ ] Publishing executable binaries to public download mirrors or websites.
- [ ] Deploying public website content or changing DNS/hosting records.
- [ ] Uploading AAB or APK packages to Google Play Console (Internal, Closed, or Production tracks).
- [ ] Uploading `.xcarchive` or IPA builds to App Store Connect / TestFlight.
- [ ] Initiating Apple App Review or publishing to the public iOS App Store.
- [ ] Publishing Linux packages to official repositories (Flathub, Canonical Snapcraft, Launchpad PPA, Fedora Copr).
- [ ] Sending public launch announcements, press releases, or social media broadcasts.

---

## 3. Pre-Publication Verification Checklist

Before the repository owner signs off on public release, every gate must be validated:

### Technical Quality
- [x] All 129 automated tests passing across shared, desktop, and mobile suites (`0 failures`).
- [x] Detekt static analysis passing with `0 weighted violations`.
- [x] R8 minified Android AAB built against Target SDK 36.
- [x] macOS standalone DMG built with self-contained Temurin JVM and 50 relocatable native dylibs.
- [x] Zero open P0 or P1 release-blocking defects in `docs/BUG_TRIAGE.md`.

### Data Safety & Privacy
- [x] Zero embedded telemetry, advertising, or analytics SDKs in distributed binaries.
- [x] Non-destructive SQLite schema migration verified from v0/v1/v2/v3 to v4.
- [x] `SafeLogger` verified to sanitize filesystem user paths and URL tokens.
- [x] Diagnostic export verified to block system root directory writes (`/etc`, `/System`, `C:\Windows`).

### Legal & Licensing
- [x] Full transitive license audit complete in `docs/LICENSE_AUDIT.md`.
- [x] LGPL v2.1/v3 obligations satisfied for libmpv, FFmpeg, and libass.
- [x] CycloneDX 1.5 SBOM generated in `docs/sbom.json`.
- [x] Third-party notices compiled in `docs/THIRD_PARTY_NOTICES.md`.

### Signing & Integrity
- [ ] Google Play upload key provided and verified by owner.
- [ ] Apple Developer ID Application certificate configured and notarization stapled on macOS DMG.
- [ ] Apple Developer Team provisioning profile configured for iOS App Store.
- [ ] Authenticode Code Signing Certificate configured for Windows EXE/MSI installers.
- [x] SHA-256 cryptographic digests generated in `docs/SHA256SUMS.txt`.

---

## 4. Release Decision Authority & Record

| Role | Designee | Gate Responsibility | Sign-Off Status |
| :--- | :--- | :--- | :---: |
| **Principal Architect** | Antigravity AI Lead | Core architecture, engine bridges, data safety | **APPROVED (`1.0.0-rc.1`)** |
| **Release Engineer** | Antigravity AI Lead | Packaging, CI/CD, SBOM, checksums | **APPROVED (`1.0.0-rc.1`)** |
| **Security & Privacy Lead** | Antigravity AI Lead | Zero telemetry, SafeLogger, redactions | **APPROVED (`1.0.0-rc.1`)** |
| **Repository Owner** | Repository Owner | Signing credentials, store submission, public release | **PENDING EXPLICIT USER AUTHORIZATION** |
