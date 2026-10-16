# Stream URL Security & Sanitization (Milestone 54)

## 1. Threat Model & Mitigations

Network media playback accepts arbitrary strings from the user. Vimivo applies rigorous security validation before passing any address to native decoder pipelines.

### Threat 1: Command Injection & Subprocess Shelling
- **Mitigation**: Vimivo never uses command-line interpolation, shell execution, or system scripts to stream URLs. Desktop `MpvPlayerEngine` passes URLs via the typed JNA API `mpv_command(handle, StringArray("loadfile", url, "replace"))`.

### Threat 2: Embedded Credentials in URLs
- **Vulnerability**: URLs formatted as `https://user:password@host/path`.
- **Mitigation**: `StreamValidator` actively detects the `@` symbol in the authority component and strictly rejects the URL with:
  *"URLs containing embedded credentials are not permitted for security reasons."*

### Threat 3: Arbitrary File Execution via Stream Entry Point
- **Vulnerability**: Supplying `file:///etc/passwd` or `file:///C:/Windows` in network input fields.
- **Mitigation**: `StreamValidator` explicitly rejects the `file:`, `content:`, `javascript:`, and `data:` schemes in network entry points.

### Threat 4: Sensitive Token Leakage in Recent History and Logs
- **Vulnerability**: Temporary session tokens (e.g. `?token=XYZ&signature=123`) persisted in plaintext or written to logs.
- **Mitigation**:
  - `DesktopStreamHistoryRepository` strips sensitive query parameters (`token`, `sig`, `signature`, `auth`, `key`, `pass`, `secret`) before writing to disk.
  - Diagnostics and logs employ `SafeLogger` to truncate or sanitize URLs.

### Threat 5: TLS Certificate Bypasses
- **Mitigation**:
  - Desktop `libmpv`: Configured with `tls-verify=yes`.
  - Android Media3: Enforces default Android Network Security Configuration.
  - iOS AVPlayer: Enforces system App Transport Security (ATS).
  - No global trust managers or disabled SSL validation exists in the repository.

---

## 2. HTTP Error Classification

| HTTP Status Code | User-Facing Explanation | Reconnection Eligible |
| :---: | :--- | :---: |
| `401 Unauthorized` | "The stream requires authorization credentials." | No (Permanent) |
| `403 Forbidden` | "Access to this stream was forbidden by the server." | No (Permanent) |
| `404 Not Found` | "The stream resource was not found on the server." | No (Permanent) |
| `408 Request Timeout` | "The media stream connection timed out." | Yes (Transient) |
| `410 Gone` | "The stream has expired or is no longer available." | No (Permanent) |
| `429 Too Many Requests`| "The server rate limited the connection. Please wait before retrying." | Yes (Exponential backoff) |
| `500 Server Error` | "The remote media server encountered an internal error." | Yes (Transient) |
| `502 / 503 / 504` | "The remote media server is temporarily unreachable." | Yes (Transient) |
