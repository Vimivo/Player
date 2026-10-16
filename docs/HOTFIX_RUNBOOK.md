# Vimivo — Emergency Hotfix Runbook

This operational runbook provides step-by-step procedures for addressing critical production defects, broken installers, database migration failures, or security emergencies.

---

## 1. Hotfix Classification

| Severity | Criteria | Response Target | Action Required |
|---|---|---|---|
| **P0 (Critical)** | App crashes on launch, data loss, database corruption, remote exploit | Immediate (< 12 hours) | Halt pending releases, branch `hotfix/vX.Y.Z`, implement fix, run regression tests, publish patch |
| **P1 (High)** | Major audio/video codec failure, installer broken on a primary platform | Fast (< 48 hours) | Branch `hotfix/vX.Y.Z`, test across affected OS, build and verify patch |
| **P2 (Medium)** | Non-crashing functional defect, cosmetic UI flaw | Next minor release | File bug in defect register, schedule for upcoming release candidate |

---

## 2. Emergency Operational Checklist

1. **Verify & Isolate**:
   - Confirm defect with exact reproduction steps on target OS.
   - Collect sanitized logs using `PrivacySafeDiagnostics`.
2. **Branch from Release Tag**:
   ```shell
   git checkout -b hotfix/v1.0.1 v1.0.0
   ```
3. **Write Failing Regression Test**:
   - Add a test in `:desktopApp:desktopTest`, `:shared:desktopTest`, or `:androidApp:testDebugUnitTest`.
   - Confirm test fails prior to fix.
4. **Apply Minimal Fix**:
   - Strictly touch only code required to resolve the defect. Avoid refactoring or adding unrelated features.
5. **Verify Quality Gate Locally**:
   ```shell
   ./scripts/verify_all.sh
   ```
6. **Trigger CI Workflow**:
   - Push branch and run `.github/workflows/hotfix-release.yml`.
7. **Obtain Explicit Authorization**:
   - Confirm manual token `YES_I_CONFIRM_HOTFIX_RELEASE` in GitHub Actions approval environment.
8. **Merge Back to Main**:
   ```shell
   git checkout main
   git merge hotfix/v1.0.1
   git push origin main
   ```

For detailed sequence diagrams and release guidelines, see [Hotfix Procedure](HOTFIX_PROCEDURE.md) and [Incident Response](INCIDENT_RESPONSE.md).
