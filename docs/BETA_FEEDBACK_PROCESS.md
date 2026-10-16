# Vimivo Beta Feedback & Defect Triage Process

## 1. Overview & Objectives

This document establishes the structured process for handling tester reports, deduplicating issues, triaging severity, performing root-cause analysis, and verifying fixes with automated regression testing.

---

## 2. Issue Lifecycle & Status Workflow

```
[New Report via GitHub Issue Form]
                 │
                 ▼
          [needs-triage]
                 │
      ┌──────────┴──────────┐
      ▼                     ▼
[needs-reproduction]   [confirmed-bug]
      │                     │
      │                     ▼
      │               [in-progress]
      │                     │
      │                     ▼
      └──────────────>[verified-fixed] (Regression test added + verified)
                            │
                            ▼
                         [closed]
```

### Official Issue Labels
- `bug`: Confirmed defect.
- `regression`: Broken functionality that worked in an earlier release.
- `needs-triage`: Newly submitted report awaiting initial engineering review.
- `needs-reproduction`: Additional reproduction steps, media samples, or diagnostic logs required.
- `confirmed-bug`: Defect successfully reproduced in internal test environment.
- `release-blocker`: Any unresolved P0 or critical P1 issue that halts Release Candidate builds.
- `verified-fixed`: Defect resolved by merged code and backed by regression test.

---

## 3. Severity Matrix & Definitions

| Severity | Operational Impact | Criteria | Response SLA |
| :--- | :--- | :--- | :--- |
| **P0 — Critical** | **Release Blocker** | • Unrecoverable user data loss (playlists, history, settings corrupted)<br>• Security vulnerability or credentials leak<br>• Application crashes on launch / bootstrap failure<br>• Core audio/video playback completely non-functional<br>• Dangerous filesystem or migration failure | Immediate triage; blocks all release candidate builds |
| **P1 — High** | **Release Blocker (Unless Explicitly Accepted)** | • Frequent playback interruptions (stutter, unexpected halts)<br>• Persistent queue loss during ordinary background transitions<br>• Major accessibility barriers preventing screen-reader navigation<br>• Video renderer freeze / hardware decoder context deadlock<br>• Severe memory leaks during prolonged sessions (>100MB/hr) | Triaged within 24h; resolved before general public release |
| **P2 — Medium** | Non-Blocking Defect | • Optional feature malfunction (e.g. lyrics display error)<br>• Non-standard media format parsing failure<br>• UI layout misalignment or clipped text on specific aspect ratios<br>• Minor persistence synchronization delays | Scheduled for next maintenance sprint |
| **P3 — Low** | Polish & Cosmetics | • Minor animation stutter<br>• Inconsistent padding or margin<br>• Tooltip phrasing or typo | Addressed during routine UI polish |

---

## 4. Deduplication Guidelines

Before confirming an issue, engineers compare against existing open and closed issues using:
1. **Error Signature & Stack Trace**: Match top 3 frames of sanitized stack trace.
2. **Platform & Backend**: Verify if symptom is backend-specific (e.g. libmpv vs Media3) or cross-platform (Compose UI, domain logic, SQLite).
3. **Media Format / Codec**: Verify whether failure is isolated to specific container atoms or corrupted metadata tags.

*Policy*: Unrelated bugs with similar user symptoms (e.g., "audio stopped playing") must never be merged if the underlying causes differ.

---

## 5. Root-Cause Analysis (RCA) Protocol

For all confirmed P0 and P1 defects, engineers must follow the 14-step RCA protocol:
1. Identify affected platform and execution environment.
2. Formulate a minimal, deterministic reproduction scenario.
3. Capture sanitized diagnostic logs via `PrivacySafeDiagnostics`.
4. Isolate the failing subsystem (UI, Domain, Persistence, Native Engine).
5. Trace the exact code path and identify concurrency/state invariant violations.
6. Determine root cause (e.g., race condition, uncaught exception, memory leak, schema mismatch).
7. Design the minimal, non-breaking fix.
8. Implement code correction adhering to architecture rules.
9. Write a deterministic automated regression test that fails without the fix and passes with it.
10. Run focused unit/integration tests.
11. Run cross-platform test suites (`./gradlew detekt :shared:desktopTest :desktopApp:desktopTest`).
12. Verify fix in actual runtime environment.
13. Document findings in issue tracker and update `IMPLEMENTATION_LEDGER.md`.
14. Commit verified code and regression test with descriptive Git message.
