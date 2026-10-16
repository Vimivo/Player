# Vimivo Closed Beta Test Plan

## 1. Executive Summary & Philosophy

Vimivo is a privacy-first, ad-free, account-free universal music and video player built with Kotlin Multiplatform and Compose Multiplatform.
This test plan defines the strategy, tester cohort design, validation gates, and operational boundaries for private, controlled testing prior to public release.

### Core Testing Principles
1. **Zero Mandatory Cloud Dependencies**: Core local media playback, library browsing, playlists, and settings must function 100% offline.
2. **Absolute Privacy**: No tracking SDKs, analytics, or automated telemetry. Diagnostic reports are strictly user-initiated, local-only, and pre-redacted.
3. **Data Safety**: Beta software must never modify, overwrite, or delete original user media files. SQLite persistence migrations must preserve playlists, history, bookmarks, and queue state non-destructively.
4. **Honest Readiness Accounting**: Beta enrollment and testing statuses reflect only verified actions. When external testing has not begun, status is recorded explicitly as `AWAITING AUTHORIZED TESTERS`.

---

## 2. Beta Readiness Gates

To avoid premature public exposure, Vimivo enforces three discrete readiness levels:

```
[Level 1: Internal QA Ready]
       │ (100% unit/integration tests pass, native backend operational)
       ▼
[Level 2: Closed Beta Ready]
       │ (Signed/sandboxed artifacts, diagnostics exporter, feedback forms, guides)
       ▼
[Level 3: Release Candidate Ready]
         (P0/P1 defects remediated, clean installs verified, upgrade paths tested, independent platform Go/No-Go)
```

### Level 1 — Internal QA Ready (CURRENT STATUS: PASSED)
- [x] Application compiles cleanly on local host and CI runners.
- [x] Shared domain logic, database migrations (v0->v4), and queue contracts automated tests pass.
- [x] Desktop (macOS/JVM with libmpv) and Android (Media3 ExoPlayer) playback engines verified.
- [x] Zero data loss during simulated crashes or process interruption.

### Level 2 — Closed Beta Ready (CURRENT STATUS: READY FOR DISTRIBUTION)
- [x] Versioned candidate artifacts generated with SHA-256 integrity checksums.
- [x] Controlled distribution pipelines prepared (Android AAB/APK, macOS DMG, Windows NSIS/MSI, Linux DEB/RPM/AppImage).
- [x] Privacy-safe diagnostic exporter implemented in shared & desktop codebases.
- [x] Structured GitHub Issue Forms configured (`bug_report.yml`, `feature_request.yml`).
- [x] Beta tester documentation, scenario checklists, and privacy pledges published.
- [x] Zero unresolved P0 defects in master branch.

### Level 3 — Release Candidate Ready (CURRENT STATUS: IN STABILIZATION)
- [x] Regression test suites automated and verified across all supported modules.
- [x] Database non-destructive upgrade verified across historical schema versions.
- [x] Defect triage system established with explicit P0-P3 classifications.
- [ ] Field beta telemetry/feedback evaluated (Blocked: Awaiting external authorized testers).
- [ ] Developer account signing/notarization completed with owner credentials.

---

## 3. Tester Cohort Matrix & Coverage Target

| Cohort Category | Target Environments | Hardware Scope | Focus Areas | Current Status |
| :--- | :--- | :--- | :--- | :--- |
| **Android Phone** | Android 12 – 16 | Pixel, Samsung, Xiaomi | MediaStore query, background audio service, notification controls, audio focus interruptions | Internal QA Verified / Beta Candidate Ready |
| **Android Tablet** | Android 13 – 16 | Samsung Tab, Pixel Tablet | Adaptive Compose layout, split-screen, landscape playback | Internal QA Verified / Beta Candidate Ready |
| **iOS / iPadOS** | iOS 16 – 18 | iPhone 13+, iPad Air/Pro | AVPlayer backend, AVAudioSession background audio, Now Playing info center | Architecture Validated / Distribution Blocked (No Owner Team Signing) |
| **macOS Desktop** | macOS 13, 14, 15 | Apple Silicon (arm64), Intel (x86_64) | libmpv rendering, Cocoa window integration, Retina scaling, DMG drag-install | Internal QA Verified (Apple Silicon) / DMG Built |
| **Windows Desktop** | Windows 10, 11 (x64) | Intel / AMD / NVIDIA GPUs | Direct3D/OpenGL surface, libmpv.dll loading, NSIS installer, High-DPI | Automated Packaging Ready / Clean Install Verified via CI |
| **Linux Desktop** | Ubuntu 22.04/24.04, Fedora 39/40, Debian 12 | GNOME (Wayland/X11), KDE Plasma | libmpv.so linkage, MPRIS D-Bus controls, DEB/RPM/AppImage packages | Packaging Validated / X11 Smoke-Tested |

---

## 4. Hardware & Peripheral Stress Targets

To validate edge-case multimedia scenarios, testing covers:
- **Audio Output Routes**: Built-in speakers, 3.5mm analog headsets, Bluetooth LE / AAC / LDAC headphones, USB-C High-Resolution DACs (up to 24-bit/192kHz).
- **Display Configurations**: Standard 1080p, 4K UHD, Ultrawide (21:9), variable refresh rate (120Hz ProMotion), multi-monitor dragging.
- **Storage Topologies**: Internal NVMe/UFS storage, external exFAT/NTFS USB-C thumb drives, SD cards, read-only mounted filesystems.
- **Library Scales**:
  - Small: 50–200 tracks / 10 videos (Rapid smoke testing)
  - Medium: 1,000–3,000 tracks / 100 videos (Index & scroll validation)
  - Large: 10,000+ tracks / 1,000 videos (Memory footprint & SQL query optimization)

---

## 5. Participation & Feedback Governance

1. **No Cold Recruitment**: Beta invitations are extended strictly to authorized cohorts via opt-in agreements.
2. **Privacy Guarantees**:
   - Tester email addresses and names are never committed to source control or published.
   - All bug reports utilize local file export; users review every line before submission.
3. **Tracking Status**:
   - Closed Beta Execution: **AWAITING AUTHORIZED TESTERS**
   - No synthetic feedback or fabricated crash statistics will be recorded.
