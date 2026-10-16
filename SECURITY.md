# Security Policy

Vimivo takes user privacy and software security seriously. As an offline-first, local media player, Vimivo has no cloud backend, collects no user credentials, and transmits zero telemetry.

---

## 1. Supported Versions

| Version | Supported | Security Notes |
|---|---|---|
| `1.0.x` | Yes | Active maintenance and security patches |
| `< 1.0.0` | No | Pre-release beta versions; update to latest release candidate |

---

## 2. Reporting a Vulnerability

If you discover a security vulnerability in Vimivo, please report it responsibly:

1. **Private GitHub Security Advisory**:
   - Navigate to the **Security** tab of the Vimivo repository.
   - Click **Report a vulnerability** to submit a private security report.
2. **Alternative Security Contact**:
   - If GitHub Security Advisories are unavailable, submit a confidential issue marked `[SECURITY]` or contact the project maintainers through official channels.

### Information to Include:
- Affected Vimivo version and platform (Android, iOS, macOS, Windows, Linux).
- Detailed description of the vulnerability and potential impact.
- Step-by-step reproduction instructions or proof-of-concept.
- Any mitigation you have identified.

Please **do not** file public GitHub issues for undisclosed security vulnerabilities.

---

## 3. Scope & Threat Model

Vimivo's core security commitments:
- **Local Filesystem Isolation**: The player only reads and indexes files within user-authorized directories or Storage Access Framework grants.
- **Credential Storage**: Network credentials (e.g. for authorized network streams) are stored exclusively in OS-level credential managers (Android Keystore, macOS Keychain, Windows Credential Manager, Linux Secret Service).
- **No Remote Telemetry**: Vimivo never transmits playback history, file names, or metadata to external servers.
- **Memory Safety**: Native multimedia bindings (`libmpv`, FFmpeg) are executed with bounded memory buffers and defensive input validation.
