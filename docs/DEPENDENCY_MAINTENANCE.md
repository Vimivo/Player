# Vimivo Dependency Security & Long-Term Maintenance Policy

> [!NOTE]
> This policy outlines the dependency update cadence, supply-chain verification, and native multimedia library maintenance for Vimivo.

---

## 1. Dependency Management Guidelines

1. **Centralized Version Catalog**: All Gradle dependencies and plugins are pinned in `gradle/libs.versions.toml`. No ad-hoc dynamic version strings (`+` or `latest.release`) are permitted.
2. **Automated Dependabot Pull Requests**: Dependabot is configured (`.github/dependabot.yml`) to scan weekly for updates to Gradle libraries and GitHub Actions.
3. **Automated Security CI**: `.github/workflows/dependency-security.yml` generates a complete dependency inventory and audits supply-chain integrity on every pull request touching dependencies.
4. **No Unreviewed Major Upgrades**: Major library upgrades require thorough regression testing across all 5 target platforms before merging.

---

## 2. Native Multimedia Library Inventory

| Library / Framework | Platform Target | Primary Role | Update Cadence | Security Exposure |
| :--- | :--- | :--- | :--- | :--- |
| **`libmpv`** (libmpv2) | macOS, Windows, Linux | Desktop hardware/software multimedia decoding & rendering | Semi-annual or per CVE | Demuxing & codec vulnerabilities |
| **`androidx.media3`** | Android | Primary mobile playback engine (ExoPlayer) | Quarterly / stable releases | Network streaming & container parsing |
| **`LibVLC`** | Android | Mobile fallback engine for exotic codecs | Annual / LTS releases | Exotic demuxers & subtitles |
| **`AVFoundation`** | iOS / iPadOS, macOS | Platform-native Apple hardware playback | Managed by Apple OS releases | Low (OS-sandboxed) |
| **`SQLite`** | All platforms | Local media, playlist, and queue persistence | Maintained with Kotlin/platform | Low (local database) |

---

## 3. Vulnerability Triage Protocol

When a vulnerability is disclosed in a bundled or transient dependency:

1. **Exploitability Assessment**: Determine whether the vulnerable API surface is exposed to untrusted user input or malicious network streams.
2. **Platform Scoping**: Identify which platform builds bundle the affected library.
3. **Mitigation / Patch**:
   - If an official patched library version is available, update `gradle/libs.versions.toml` and run regression test suites.
   - If no upstream patch is available, disable the vulnerable parser/feature in `EngineCapabilities` or fallback to alternative engine.
4. **Emergency Hotfix Release**: If rated Critical/High with active remote exploitability, execute the Hotfix Procedure (`docs/HOTFIX_PROCEDURE.md`).
