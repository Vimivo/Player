# Streaming Privacy & Data Boundaries (Milestones 53 & 54)

Vimivo adheres to strict privacy guarantees across all media streaming functionality.

---

## 1. Zero-Cloud Guarantee

1. **No Online Media Catalog**: Vimivo never connects to online content marketplaces, YouTube, Spotify, TikTok, or remote artist catalogs.
2. **No Streaming History Telemetry**: Stream URLs are kept strictly local on device in user-accessible storage (`recent_streams.json` on Desktop, private `DataStore` on Android). No URL is ever transmitted to Vimivo servers or third-party analytical endpoints.
3. **No Web Scraping**: Vimivo does not scrape web pages, steal cookies, or extract media from unauthorized third-party sites.
4. **No Mandatory Accounts**: Playback of direct streams requires no login, email, phone number, or device identifier.

---

## 2. Redaction & Credential Hygiene

1. **Query Token Redaction**:
   - `DesktopStreamHistoryRepository` sanitizes URL query parameters matching `token`, `sig`, `signature`, `auth`, `key`, `pass`, and `secret`.
2. **Prohibited Embedded Credentials**:
   - URLs formatted with embedded `user:password@` credentials are automatically rejected to prevent credential persistence in local files or system logs.
3. **Diagnostic Sanitization**:
   - `DesktopDiagnosticExporter` strips full streaming URLs from exported JSON reports, recording only protocol classification and codec details.
