# Vimivo — Post-Release Maintenance, Monitoring & Long-Term Stability Guide

> [!IMPORTANT]
> This document establishes the operational framework for maintaining Vimivo following an authorized production release. It covers defect reporting, privacy-first diagnostic collection, emergency incident response, hotfix procedures, database migration safety, dependency management, and long-term roadmap planning.

---

## 1. Core Operating Principles & Boundaries

Vimivo is an ad-free, account-free, login-free, local-first audio and video player. Post-release maintenance is governed by strict boundaries:

1. **Zero Telemetry / No Remote Tracking**: Vimivo never collects background analytics, crash dumps, telemetry beacons, or user engagement metrics.
2. **Local Data Primacy**: User databases, playlists, favorites, playback history, bookmarks, and settings reside strictly on the user's local filesystem.
3. **No Automatic Diagnostic Uploads**: All diagnostic reports are user-initiated, locally generated, sanitized against user data leaks, and reviewable prior to sharing.
4. **Targeted Remediation**: Emergency patches must be minimal, targeted, and accompanied by deterministic regression tests.

---

## 2. Bug Reporting & Triage Architecture

### 2.1 Issue Intake Channels
Users and beta testers report defects via GitHub Issues using structured YAML issue forms:
- [Bug Report](file:///Users/ta-shanto/Coding/products/Vimivo/.github/ISSUE_TEMPLATE/bug_report.yml): General UI defects, navigation failures, or background lifecycle crashes.
- [Playback / Codec Issue](file:///Users/ta-shanto/Coding/products/Vimivo/.github/ISSUE_TEMPLATE/playback_issue.yml): Decoding errors, stuttering, dropped frames, audio sync offsets, or subtitle rendering defects.
- [Accessibility Issue](file:///Users/ta-shanto/Coding/products/Vimivo/.github/ISSUE_TEMPLATE/accessibility_issue.yml): Screen reader (TalkBack/VoiceOver) label defects, keyboard focus traps, or touch target sizing.
- [Feature Request](file:///Users/ta-shanto/Coding/products/Vimivo/.github/ISSUE_TEMPLATE/feature_request.yml): Scope-aligned feature requests respecting local-first constraints.

### 2.2 Severity Classification Matrix

| Severity | Definition | Operational Target | Release Blocker? |
|---|---|---|---|
| **P0 — Critical** | User data loss, database corruption, remote execution vulnerability, app crash on bootstrap, compromised signing key. | Containment < 4h, Fix < 24h | **YES** |
| **P1 — High** | Core playback workflow failure, persistent queue desynchronization, major platform-specific crash, broken installer. | Containment < 12h, Fix < 48h | **YES** |
| **P2 — Medium** | Format decoding failure on edge codec, non-fatal background audio glitch, secondary settings UI inconsistency. | Fix within 7 days | No |
| **P3 — Low** | Minor cosmetic defect, alignment or margin inconsistency, typography edge cases. | Scheduled backlog | No |

---

## 3. Privacy-Preserving Diagnostics & Redaction Standards

Vimivo includes a built-in diagnostic report generator (`DesktopDiagnosticExporter` on desktop, `PrivacySafeDiagnostics` in shared core). When users export diagnostic information, the following redaction standards are enforced:

1. **User Filesystem Redaction**:
   - macOS: `/Users/<username>/...` -> `~/user/...`
   - Linux: `/home/<username>/...` -> `~/user/...`
   - Windows: `C:\Users\<username>\...` -> `C:\Users\user\...`
2. **URL Credential & Token Redaction**:
   - Embedded credentials: `https://user:password@host/stream.m3u8` -> `https://[REDACTED]@host/stream.m3u8`
   - Sensitive query parameters: `token`, `auth`, `key`, `secret`, `password`, `passwd`, `sig`, `signature`, `access_token`, `api_key`, `client_secret`, `session_token`, `jwt`, `ticket` are replaced with `[REDACTED]`.
   - Authorization headers: `Bearer ...` and `Basic ...` are redacted.
3. **Protected Path Traversal Prevention**:
   - `DesktopDiagnosticExporter` resolves canonical symlinks and refuses to write reports to root system locations (`/System`, `/etc`, `/private/etc`, `/bin`, `/sbin`, `/usr`, `/var/root`, `C:\Windows`, `C:\Program Files`).
4. **In-Memory Ring Buffer**:
   - `DiagnosticBuffer` holds at most 150 events in a fixed ring buffer, preventing unbounded memory growth during continuous multi-hour sessions.

---

## 4. Incident Response & Escalation Protocol

When an emergency defect is reported in production:

```mermaid
flowchart TD
    A["1. Ingestion & Triage"] --> B{"Severity Assessment"}
    B -->|"P0 / P1"| C["2. Immediate Containment"]
    B -->|"P2 / P3"| D["Normal Backlog Planning"]
    C --> E["3. Reproduction Test Creation"]
    E --> F["4. Targeted Fix Implementation"]
    F --> G["5. Quality Gates & Regression Verification"]
    G --> H["6. Patch Release (hotfix/vX.Y.Z)"]
    H --> I["7. Post-Incident Review (PIR)"]
```

### Steps:
1. **Ingest & Confirm**: Reproduce against public test fixtures or synthetic test data. Verify whether user data or security is at risk.
2. **Containment**: If distributed via staged rollout, halt rollout in Google Play Console or update distribution manifest.
3. **Reproduction Test**: Author a deterministic failing test in `shared/src/commonTest` or `desktopApp/src/desktopTest`.
4. **Targeted Fix**: Implement the minimal necessary fix on a dedicated `hotfix/vX.Y.Z` branch.
5. **Quality Gates**: Execute `./scripts/verify_all.sh` and iOS simulator tests.
6. **Release Authorization**: Obtain maintainer authorization before publishing.
7. **Post-Incident Review**: Document root cause, timeline, impact, and prevention measures in `docs/BUG_TRIAGE.md`.

---

## 5. Emergency Hotfix Procedure

Hotfixes follow the workflow documented in [HOTFIX_PROCEDURE.md](file:///Users/ta-shanto/Coding/products/Vimivo/docs/HOTFIX_PROCEDURE.md):

1. **Branch**: `git checkout -b hotfix/v1.0.1 v1.0.0`
2. **Test First**: Write automated reproduction test verifying failure.
3. **Patch**: Apply minimal code correction.
4. **Verify**:
   ```bash
   ./scripts/verify_all.sh
   ./scripts/validate_docs.sh
   ```
5. **CI Trigger**: Trigger `.github/workflows/hotfix-release.yml` with maintainer sign-off token.
6. **Merge Back**: Merge hotfix branch back into `main` after verification.

---

## 6. Dependency & Supply Chain Security Policy

1. **Version Pinning**: All dependencies are centralized in [libs.versions.toml](file:///Users/ta-shanto/Coding/products/Vimivo/gradle/libs.versions.toml). No floating or dynamic versions are permitted.
2. **Automated Scanning**: Dependabot runs weekly scans via [.github/dependabot.yml](file:///Users/ta-shanto/Coding/products/Vimivo/.github/dependabot.yml).
3. **Software Bill of Materials**: CycloneDX 1.5 SBOM is maintained at [sbom.json](file:///Users/ta-shanto/Coding/products/Vimivo/docs/sbom.json).
4. **Native Decoder Updates**: `libmpv` (desktop) and `Media3` (Android) are updated following stable upstream releases or immediate CVE disclosures.

---

## 7. Database Migration Safety & Local Data Recovery

Vimivo uses SQLite with Write-Ahead Logging (WAL) and foreign keys enabled. For all future database updates:

1. **Non-Destructive Invariant**: Existing tables, user playlists, playback history, resume offsets, favorites, and metadata overrides must never be dropped or cleared during upgrades.
2. **Atomic Transactions**: Every schema migration step must execute inside `connection.transaction { ... }`. If an exception occurs, the transaction rolls back cleanly.
3. **Sequential Migrations**: Sequential migrations (v1 -> v2 -> ... -> vN) are tested via `DesktopDatabaseMigrationTest.kt`.
4. **Never Overwrite Real Databases in Tests**: Tests must always use synthetic fixtures in temporary directories.
5. **Recovery on Corruption**: If SQLite reports database corruption, Vimivo attempts read-only vacuum recovery to a timestamped backup before initiating a fresh schema.

---

## 8. Multiplatform Regression Prevention Policy

To prevent regressions across platforms:
- **Shared Contracts**: Pure Kotlin business logic in `shared/src/commonMain` must never import Android `Context`, Media3, JNA, libmpv, or Cocoa types.
- **Automated Quality Gate**: Every pull request must pass `./scripts/verify_all.sh`:
  - Step 1: Detekt static analysis (0 violations required).
  - Step 2: Shared module tests (`:shared:desktopTest`).
  - Step 3: Desktop Kotlin compilation (`:desktopApp:compileKotlinDesktop`).
  - Step 4: Desktop test suite (`:desktopApp:desktopTest`).
  - Step 5: Android unit tests (`:androidApp:testDebugUnitTest`).
  - Step 6: Android debug APK assembly (`:androidApp:assembleDebug`).
- **iOS Quality Gate**: Native tests run on iOS Simulator via `xcodebuild test`.
- **Documentation Gate**: 100% of internal documentation links validated via `./scripts/validate_docs.sh`.

---

## 9. Post-Release Maintenance Roadmap

### A. Immediate Post-Launch (Week 1–2)
- Monitor initial user feedback via GitHub Issues.
- Track stability reports from private beta cohort.
- Validate crash-free sessions across Android 12 through Android 15.

### B. Short Term (Month 1–3)
- Modernize macOS video rendering layer to Metal (`CAMetalLayer`) to deprecate OpenGL layer.
- Expand SAF document provider support on Android for secondary SD cards and USB OTG drives.
- Refine Linux Wayland fractional scaling and MPRIS track progress positioning.

### C. Medium Term (Month 3–6)
- Expand embedded subtitle styler options (font selection, outline thickness).
- Implement non-destructive playlist export to standard XSPF format.
- Add hardware-accelerated video decoding selection toggle (Auto / Software / Hardware Preferred) in settings UI.

### D. Long Term (Month 6+)
- Investigate PipeWire native audio sink backend for Linux desktop.
- Multiplatform smart playlist AST condition builder enhancements.
- Continuous dependency and toolchain maintenance (Kotlin 2.x, AGP updates).
