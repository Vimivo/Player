# Streaming Network Recovery & Reconnection (Milestone 54)

Network playback is subject to transient disconnections, DNS timeouts, Wi-Fi handoffs, and packet stalls. Vimivo employs a bounded, exponential backoff reconnection policy that prevents infinite retry storms and avoids duplicating player engines.

---

## 1. Bounded Exponential Backoff Strategy (`StreamReconnectionPolicy`)

```kotlin
// Maximum attempts: 3
// Base delay: 1,000 ms
// Multiplier: 2.0x
// Max delay cap: 6,000 ms

Attempt 1: wait 1,000 ms
Attempt 2: wait 2,000 ms
Attempt 3: wait 4,000 ms
Attempt 4+: Stop reconnection, transition to PlaybackStatus.Error
```

### Cancellation Guarantees
- **User Stop Action**: Explicitly sets `userCancelled = true` and aborts all pending reconnect jobs.
- **Source Switch**: Loading a new media source cancels all reconnection coroutines on the prior stream.
- **No Duplicate Sessions**: The existing player engine is reused with `loadfile ... replace`; no second player is created.

---

## 2. Recoverability Matrix

| Failure Classification | Recoverable? | Reconnection Behavior |
| :--- | :---: | :--- |
| `NETWORK_DISCONNECTED` | Yes | Retries with backoff up to 3 times. |
| `SERVER_TIMEOUT` | Yes | Retries with backoff up to 3 times. |
| `DNS_LOOKUP_FAILED` | Yes | Retries with backoff up to 3 times. |
| `408 Request Timeout` | Yes | Retries with backoff up to 3 times. |
| `429 Too Many Requests` | Yes | Backs off before retrying. |
| `INVALID_URL` | **No** | Terminal error immediately reported. |
| `UNSUPPORTED_PROTOCOL` | **No** | Terminal error immediately reported. |
| `WEBPAGE_NOT_DIRECT_STREAM` | **No** | Terminal error immediately reported. |
| `HTTP_FORBIDDEN (403)` | **No** | Terminal error immediately reported. |
| `NOT_FOUND (404)` | **No** | Terminal error immediately reported. |
| `EXPIRED_URL (410)` | **No** | Terminal error immediately reported. |
