# Vimivo — Streaming Security Audit & Threat Model

**Milestones**: 57 & 58  
**Target Release**: `1.0.0-rc.1`  
**Git HEAD**: `0d7ecba`  
**Date**: 2026-10-16  

---

## 1. Security Threat Model & Trust Boundaries

Vimivo enforces strict security boundaries between untrusted remote network inputs and the local client runtime:

```mermaid
graph LR
    User[User Input URL] -->|Strict Sanitizer| Gate[StreamValidator]
    Gate -->|Valid Direct Stream| Client[Native Network Client]
    Gate -->|Reject Webpages / Schemes / Tokens| Err[Actionable User Rejection]
    Client -->|TLS 1.2/1.3 Certificate Validation| Remote[Remote Media Server]
    Remote -->|Demuxed Media Packets| MPV[libmpv / Media3 Decoders]
    MPV -->|Hardware Surface Only| Screen[Display & Audio Output]
```

### 1.1 Trust Boundaries & Attack Vectors

| Trust Boundary | Attack Vector Evaluated | Defense Mechanism | Residual Risk |
| :--- | :--- | :--- | :--- |
| **User URL Input** | Shell injection / command interpolation | Direct programmatic API (libmpv C API / ExoPlayer MediaItem); zero shell execution | None |
| **URL Schemes** | `file://`, `data://`, `javascript://`, `content://` | Whitelist strictly limited to `http`, `https`, `rtsp`, `rtsps` | None |
| **Embedded Credentials** | `https://user:pass@host/media.mp4` | Explicit regex rejection of `@` embedded user credentials | None |
| **CRLF Injection** | Header splitting via `\r\n` | Rejection of CR, LF, and control characters in URLs and headers | None |
| **Webpage Portals** | Scraping YouTube, TikTok, Vimeo | User-friendly rejection explaining direct media stream requirement | None |
| **TLS Validation** | Man-in-the-Middle (MitM) attacks | TLS certificate verification enabled (`tls-verify=yes`, standard OS CA roots) | None |
| **Redirect Security** | Redirect loop or redirect to unsafe scheme | Max 5 redirects; target scheme must remain in `http`/`https` | None |
| **Private LAN Isolation**| Malicious SSRF / local network scanning | No automatic discovery or device probing; explicit URLs only | None |
| **Signed URLs & Tokens** | Leakage of access tokens in logs | Sensitive query parameters (`token`, `sig`, `key`, `auth`) redacted in history & diagnostics | None |
| **Untrusted Manifests** | Malformed HLS `.m3u8` or DASH `.mpd` | Native parser bounds checking; manifest size limits; no disk write side-effects | None |

---

## 2. Hardening Verifications

### 2.1 URL Parsing & Scheme Validation
Verified in `StreamValidatorTest`:
- Rejects non-media portal domains (`youtube.com`, `youtu.be`, `vimeo.com`, `tiktok.com`, `instagram.com`).
- Rejects dangerous local schemes (`file://`, `jar://`, `data://`).
- Rejects embedded user info credentials (`http://admin:secret@192.168.1.100/stream`).
- Rejects URLs containing line breaks or control characters (`\r`, `\n`).

### 2.2 Credential Protection in Diagnostics & History
Verified in `StreamHistoryRepository` and `DesktopDiagnosticReporter`:
- Query parameters containing credentials or tokens (`auth`, `signature`, `token`, `key`, `secret`, `jwt`, `access_token`) are replaced with `[REDACTED]` prior to storing recent stream history.
- Public diagnostic exports sanitize network URLs, keeping only protocol, host, and path extension.

### 2.3 Local Network Boundary Defense
- Vimivo does not implement background mDNS, SSDP, DLNA, or UPnP network scanning.
- If a user plays an explicit LAN RTSP camera stream (`rtsp://192.168.1.50:554/live`), connections are opened only to that specific IP and port, with no port-scanning of surrounding network nodes.
