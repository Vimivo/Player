## Summary of Changes

<!-- Provide a concise description of what this PR changes and why. -->

## Motivation & Context

<!-- Reference related issue (e.g., Fixes #123) or explain the engineering rationale. -->

## Affected Platforms

- [ ] Shared (`shared/src/commonMain`)
- [ ] Android (`androidApp`)
- [ ] macOS Desktop (`desktopApp`)
- [ ] Windows Desktop (`desktopApp`)
- [ ] Linux Desktop (`desktopApp`)
- [ ] iOS / iPadOS (`iosApp`)
- [ ] Documentation / CI

## Quality & Verification Checklist

- [ ] `./scripts/verify_all.sh` (or `./gradlew verifyAll`) completed successfully.
- [ ] `./gradlew detekt` passed with 0 weighted violations.
- [ ] Automated tests added or updated for new business logic / regressions.
- [ ] Database schema changes (if any) include non-destructive migrations and tests in `DesktopDatabaseMigrationTest`.
- [ ] Verified that original media files are never deleted or modified without explicit user approval.
- [ ] Relevant documentation updated (e.g. `docs/ARCHITECTURE.md`, `docs/FEATURE_MATRIX.md`, user guides).
- [ ] No secrets, private signing keys, or private media files are included.
