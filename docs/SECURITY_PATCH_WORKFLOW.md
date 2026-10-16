# Vimivo — Security Patching & Supply Chain Workflow

This document establishes the security response procedure, vulnerability classification methodology, and software supply chain controls for Vimivo.

---

## 1. Upstream Dependency & Advisory Monitoring

Vimivo maintains active surveillance over security advisories across all constituent layers:

| Component | Monitored Sources | Assessment Focus |
| :--- | :--- | :--- |
| **AndroidX Media3** | Google Open Source Vulnerabilities (OSV), Android Security Bulletins | Demuxer overflows, HLS/DASH parser bounds, intent handling. |
| **libmpv & FFmpeg** | FFmpeg Security Advisories, mpv git repository commits | Audio/video container demuxing vulnerabilities, memory corruption. |
| **SQLite Engine** | SQLite Consortium Release Notes, US-CERT | SQL parsing issues, B-tree corruption vectors. |
| **Kotlin & Compose** | JetBrains Security Advisories | Compiler bytecode generation, serialization deserialization safety. |
| **Build Tooling & CI** | GitHub Dependabot, GitHub Advisory Database | Gradle plugin vulnerabilities, action runner supply-chain risks. |

---

## 2. Vulnerability Severity & Exposure Assessment

Advisories are classified based on Vimivo's operational model as an offline-first, local player:

1. **Critical (P0)**:
   - Remote code execution via crafted local or user-provided streaming media (e.g. heap buffer overflow in FFmpeg demuxer or Media3 extractor).
   - Arbitrary file overwrite or execution via update check spoofing.
   - Response SLA: Immediate mitigation within 24–48 hours.
2. **High (P1)**:
   - Denial-of-service crash via malformed media headers.
   - SSRF or credential leakage via user-supplied stream URLs.
   - Response SLA: Fixed in next scheduled minor/patch release within 7 days.
3. **Medium / Low (P2/P3)**:
   - Theoretical attacks requiring existing local root/admin access to the user's filesystem.
   - Addressed in regular maintenance cycles.

---

## 3. Patch Engineering & Release Gate

1. **Triage & Reproducer**: Isolate the defect with an automated regression test reproducing the vulnerability.
2. **Dependency Update**: Bump the dependency coordinate in `gradle/libs.versions.toml`.
3. **Static Analysis**: Verify compliance with `./gradlew detekt`.
4. **Playback Verification**: Execute the full media playback regression suite (`./scripts/verify_all.sh`) to confirm no codec or protocol regression.
5. **Signed Release**: Package and sign the patch release using authenticated release keys.
