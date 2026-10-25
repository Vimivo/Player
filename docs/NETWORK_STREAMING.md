# Network streaming

Vimivo plays only URLs explicitly supplied or reopened by the user. URLs and favorites are stored locally in DataStore, are never uploaded for validation, and may be individually removed or cleared. URLs containing embedded credentials are rejected so secrets are not retained in history.

## Engine behavior

- Media3 is preferred for direct HTTP/HTTPS media, HLS, and MPEG-DASH. Adaptive rendition and track selection use Media3's native implementation.
- libmpv remains a bounded compatibility fallback for verified container, manifest, or decoder failures. Access failures, HTTP status failures, and ordinary network failures do not switch engines.
- Default engine buffering handles slow connections. Transport connection failures can reconnect up to three times; other failures require an explicit retry.
- Seeking is enabled only when the session reports the source as seekable. Indefinite-duration, non-seekable ready sources are presented as live.
- HLS and DASH are selected from `.m3u8` and `.mpd` URLs or authoritative server metadata. Mislabelled extensionless manifests may not be identifiable automatically.

## Cleartext policy

HTTPS should be used whenever available. Android cannot express a static Network Security Config allowlist for arbitrary user-entered LAN and radio hosts, so Vimivo permits cleartext at its application boundary. The practical scope is constrained by product behavior: the app has no catalogs, scraping, analytics, URL preflight service, or background URL discovery, and opens HTTP only after a direct user action.

## Manual test matrix

Use streams owned by the tester or official public samples whose terms authorize playback.

| Scenario | Expected result |
|---|---|
| Direct MP3 radio/file | Connects, buffers, plays in background, pause/resume works |
| Direct MP4 | Video surface appears; seeking follows server range support |
| HLS VOD | Adaptive playback, duration and seeking available |
| HLS live | Live and non-seekable state shown when the manifest has no DVR window |
| MPEG-DASH VOD | Adaptive playback and available audio/video tracks are exposed |
| Disconnect/reconnect | Buffering/error shown; transport retry is bounded to three attempts |
| HTTP 4xx/5xx | Meaningful server error and manual Retry; no engine retry loop |
| Expired signed URL | Unavailable/expired error; URL remains removable from history |
| Invalid URL or unsupported scheme | Rejected before playback and not added to history |
| Unsupported/malformed manifest | Meaningful manifest error; compatibility fallback remains bounded |
