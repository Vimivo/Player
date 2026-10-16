# Vimivo Diagnostic Privacy & Local Sanitization Policy

## 1. Principles of Diagnostic Privacy

Vimivo was built on the core principle that a media player should never spy on its users. Diagnostic logs exist solely to enable troubleshooting on the user's local device and must never become a vector for telemetry or personal data leakage.

### Core Guarantees:
1. **Zero Remote Telemetry**: Vimivo does not transmit logs to any remote server, Sentry instance, Firebase crashlytics, or analytics backend.
2. **Explicit User Action**: Diagnostic exports occur only when explicitly triggered by the user via the UI.
3. **Local In-Memory Sanitization**: Redaction is performed before text is displayed in preview windows or written to disk.
4. **Transparent Inspection**: Users can inspect every character of a diagnostic report prior to copying or sharing.

---

## 2. Sanitization Pipeline (`SafeLogger.kt`)

All text included in exported diagnostics, crash reports, and feedback reports passes through `dev.vimivo.core.util.SafeLogger`:

### Active Sanitization Rules:

| Pattern | Target | Redacted Output |
| :--- | :--- | :--- |
| **Authentication Tokens** | `token=eyJhbGci...`, `api_key=sk_live_...` | `token=[REDACTED]`, `api_key=[REDACTED]` |
| **Passwords & Secrets** | `password=secret123`, `passwd=myPass` | `password=[REDACTED]` |
| **Sensitive URL Query Params** | `?auth=abc&token=xyz`, `&signature=123` | `?auth=[REDACTED]&token=[REDACTED]` |
| **User Directory Paths (Unix/macOS)** | `/Users/john_doe/...`, `/home/alex/...` | `~/<redacted-path>` |
| **User Directory Paths (Windows)** | `C:\Users\JohnDoe\...` | `C:\Users\<redacted>\...` |
| **Network Credentials in URLs** | `https://user:pass@example.com` | `https://[REDACTED]@[REDACTED]` |

---

## 3. Safe Diagnostic Excerpt Composition

When the user chooses to include diagnostics in a feedback report, the attached excerpt contains:
- **Application Version**: `1.0.0-rc.1`
- **Target Platform**: `macOS Apple Silicon`, `Windows 11`, `Linux x86_64`, or `Android`
- **Operating System Version**: e.g., `macOS 15.0.1`, `Android 15 (API 35)`
- **Native Engine Version**: `libmpv 0.38.0` / `Media3 1.5.1`
- **Recent Sanitized Error Events**: Up to 10 most recent error log entries, scrubbed of user paths and query tokens.
- **Hardware Acceleration State**: Whether hardware decoding is active (`auto-safe`) or fallen back to software.

### Explicitly Excluded:
- Full media library database.
- Listening or watching history.
- Playlist names and item listings.
- User network configuration or MAC address.
- Full unredacted stack traces containing local project source paths outside the repository root.
