# Stream Network Failure Classification & Recovery Engineering (Milestones 55 & 56)

## 1. Network Failure Classification

Vimivo strictly separates network transport errors from codec/decoder errors. A media player must never claim an "unsupported video codec" when an HTTP 403 Forbidden or 404 Not Found is the root cause.

| HTTP / Network Code | Classification | Failure Reason | Retryable? | Explanation |
| :---: | :---: | :---: | :---: | :--- |
| **401 / 403** | Authorization Failure | `HTTP_FORBIDDEN` | No | Target server denied access. Permanent until user provides valid credentials or token. |
| **404** | Resource Not Found | `NOT_FOUND` | No | Target media URL does not exist on the remote host. |
| **408** | Request Timeout | `TIMEOUT` | Yes | Transient network or server delay. Suitable for bounded retry. |
| **410** | Gone / Expired | `EXPIRED_URL` | No | Signed or temporary media URL has expired. Cannot be recovered without a newly signed URL. |
| **429** | Rate Limited | `CONNECTION_FAILED` | Yes (Bounded) | Remote server requested throttling. |
| **500 - 599** | Server Error | `CONNECTION_FAILED` | Yes (Bounded) | Upstream gateway or origin server failure. |
| **Connection Reset** | Transport Interruption | `CONNECTION_FAILED` | Yes | Wi-Fi/mobile network handoff, socket reset, or packet loss. |

---

## 2. Reconnection Policy Architecture

Reconnections are governed by `StreamReconnectionPolicy`:
- **Exponential Backoff**: `delayMs = (initialDelayMs * backoffMultiplier^(attempt - 1)).coerceAtMost(maxDelayMs)`
- **Default Parameters**:
  - `maxRetries = 3`
  - `initialDelayMs = 1,000ms`
  - `backoffMultiplier = 2.0`
  - `maxDelayMs = 8,000ms`
- **Cancellation Guarantee**:
  - If `isUserCancelled == true` (e.g. user pressed Stop, changed track, or exited the player), `shouldRetry` unconditionally returns `false`.
  - Infinite retry loops are strictly prohibited to prevent battery exhaustion and network flooding.

---

## 3. Header & Credential Injection Defense

User-specified request headers (e.g. `User-Agent`, `Authorization`, `Referer`) are validated via `StreamValidator.validateHeaders`:
- Header names cannot be blank or contain `:`.
- Neither header names nor values may contain Carriage Return (`\r`) or Line Feed (`\n`) characters.
- Any attempt at CRLF response splitting or header smuggling results in immediate validation failure before dispatching to the native network client.
- Redacted display strings ensure access tokens are not leaked into logs or crash dumps.
