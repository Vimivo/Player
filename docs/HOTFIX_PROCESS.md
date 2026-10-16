# Vimivo — Hotfix Release Engineering Process

This document establishes the urgent response workflow, branching strategy, verification requirements, and release gates for out-of-band hotfixes in Vimivo.

---

## 1. Hotfix Triggers & Criteria

A hotfix is triggered exclusively by urgent, severe operational defects:
- **P0 Native Crash**: Application fails to launch on a supported OS or architecture.
- **P0 Media Security Vulnerability**: Exploitable memory corruption in media demuxers or network parsers.
- **P0 Data Loss**: Database migration failure causing loss or corruption of user playlists or personal data.
- **P0 Playback Failure**: Total failure of audio output or video rendering for standard local media.
- **P0 Update Security Defect**: Flaw in signature validation, digest checking, or downgrade prevention.

---

## 2. Step-by-Step Hotfix Workflow

```
[ Incident Confirmed & Classified P0 ]
                  │
                  ▼
   1. Hotfix Branch Creation
      git checkout -b hotfix/v1.0.x v1.0.0
                  │
                  ▼
   2. Root Cause Isolation & Reproducer Test
      Add failing regression test reproducing the exact failure mode
                  │
                  ▼
   3. Minimal Targeted Fix
      Surgical patch modifying only the defective code path
                  │
                  ▼
   4. Quality Gate Verification
      Execute ./scripts/verify_all.sh (Detekt + Shared + Desktop + Android)
                  │
                  ▼
   5. Version Monotonic Increment
      Increment PATCH version (e.g. 1.0.0 -> 1.0.1)
                  │
                  ▼
   6. Release Notes & Manifest Generation
      Update docs/RELEASE_NOTES.md and generate signed release manifest
                  │
                  ▼
   7. Maintainer Approval & Tagging
      Sign git commit and create release tag v1.0.1
```

---

## 3. Post-Hotfix Integration

Once the hotfix release candidate is tagged and released:
1. Merge the `hotfix/v1.0.x` branch back into `main` to preserve fix lineage.
2. Update `docs/IMPLEMENTATION_LEDGER.md` and `docs/DEVELOPMENT_CONTINUATION.md`.
3. Conduct a blameless post-mortem documenting root cause, detection gap, and regression test additions.
