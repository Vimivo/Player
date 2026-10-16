# Vimivo Maintenance & Technical Debt Register

## 1. Overview & Policy

A sustainable open-source product must balance new user-facing improvements with rigorous technical maintenance, security updates, dependency hygiene, and platform SDK migrations. 

**Policy**: At least 30% of engineering bandwidth per milestone is reserved for technical debt remediation, dependency security audits, and automated test maintenance.

---

## 2. Active Maintenance & Technical Debt Register

| ID | Area | Item Description | Risk / Impact | Status |
| :--- | :--- | :--- | :--- | :--- |
| **MNT-001** | Dependencies | Audit and upgrade Compose Multiplatform and Jetpack dependencies in `gradle/libs.versions.toml`. | Low | Scheduled |
| **MNT-002** | Security | Verify checksums and signatures for bundled native libraries (libmpv, FFmpeg dependencies). | Critical | In Progress |
| **MNT-003** | macOS | Monitor Apple Silicon notarization rules and Hardened Runtime entitlements updates for macOS 15+. | High | Active |
| **MNT-004** | Windows | Validate Windows MSI installer upgrades and maintain code-signing certificate renewal timeline. | Medium | Scheduled |
| **MNT-005** | Linux | Audit AppImage compatibility across newer glibc versions (Ubuntu 24.04+, Fedora 40+). | Medium | Active |
| **MNT-006** | Android | Prepare Media3 and Android 16 (Baklava / API 36) target SDK compatibility audit. | Medium | Next Cycle |
| **MNT-007** | Performance | Monitor SQLite query planning on libraries exceeding 50,000 items to avoid UI thread disk I/O. | Medium | Verified |
| **MNT-008** | Testing | Add synthetic stress tests for corrupted ID3 tags and malformed Matroska headers. | High | Complete |

---

## 3. Maintenance Cadence
- **Weekly**: Automated dependency vulnerability scanning (`./gradlew dependencyCheckAnalyze` / Dependabot).
- **Per Release Candidate**: Full security audit, license compliance check (`./scripts/verify_all.sh`), and native bundle verification.
- **Per Milestone**: Technical debt review and deprecation cleanup across `:shared`, `:desktopApp`, and `:androidApp`.
