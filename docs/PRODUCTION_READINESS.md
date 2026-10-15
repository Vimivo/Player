# Production readiness

No platform currently passes the public-distribution release gate.

| Platform | Technical status | Primary blockers |
|---|---|---|
| Android | Partial | Full current-HEAD build/device regression, release configuration, device playback, policy, and signed AAB verification remain. |
| macOS | Partial | Standalone application (`Vimivo.app`) and installable DMG (`Vimivo-1.0.0.dmg`) with self-contained JVM and 50-dylib native closure are implemented and verified. Public distribution gate remains blocked on Apple Developer ID certificate signing, Hardened Runtime, and notarization. |
| Windows | Partial | Embedded video renderer, Windows runtime tests, native DLL bundle, installed EXE/MSI verification, and signing remain. |
| Linux | Partial | Renderer/runtime validation, desktop integration, native dependency bundle, and actual package builds remain. |
| iOS | Not implemented | No iOS target, native playback adapter, persistence adapter, Xcode project, device tests, or release configuration exists. |

Passing compilation or JVM unit tests does not satisfy the native runtime, installation, security, licensing, accessibility, or signing gates.
