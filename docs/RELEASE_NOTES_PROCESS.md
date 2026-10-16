# Vimivo Release Notes Generation & Feedback Resolution Process

## 1. Release Notes Philosophy

Release notes communicate real, verified improvements to users. They must never contain marketing fluff, unreleased work, or exaggerated claims.

### Mandatory Rules:
1. **Verified Code Only**: Every line item in the changelog must correspond to merged code and passing automated tests.
2. **Clear Platform Attribution**: Indicate which platforms benefit (e.g. `[Desktop]`, `[Android]`, `[All]`).
3. **No Reporter Exposure**: Never expose community reporter identities, private emails, or private report links without explicit permission.
4. **Issue Traceability**: Link verified community issues to closed GitHub pull requests and changelog entries.

---

## 2. Release Notes Categories

Every release notes document is structured into standardized sections:

- **Highlights**: Key user-facing milestones or major capability additions.
- **Added**: New capabilities, supported codecs, or user interface improvements.
- **Fixed**: Confirmed bug fixes, stability patches, and crash remediation.
- **Accessibility**: Assistive technology enhancements (TalkBack, VoiceOver, keyboard navigation).
- **Performance**: Seek latency improvements, memory reduction, or decoding speedups.
- **Security & Privacy**: Vulnerability remediation, path sanitization enhancements.
- **Known Issues**: Transparent listing of outstanding limitations with documented workarounds.

---

## 3. Community Response Templates

When closing issues associated with a released fix:

### Template: Fix Implemented in Main
> "Thank you for reporting this issue! A fix has been implemented and verified with automated regression tests in commit `<hash>`. This fix will be included in the upcoming `<version>` release."

### Template: Fix Released in GA
> "This issue is now resolved in Vimivo `<version>`, which is available for download at `<release-link>`. Thank you for helping improve Vimivo for everyone!"

### Template: Cannot Reproduce / Needs Information
> "We attempted to reproduce this issue using `<version>` on `<platform>`, but were unable to reproduce the reported behavior with our test fixtures. Could you please provide: (1) exact media codec/container details via Settings -> Export Diagnostics, and (2) step-by-step reproduction instructions? Thank you!"
