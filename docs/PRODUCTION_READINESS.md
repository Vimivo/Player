# Production readiness

No platform currently passes the public-distribution release gate.

| Platform | Technical status | Primary blockers |
|---|---|---|
| Android | Partial | Full current-HEAD build/device regression, release configuration, device playback, policy, and signed AAB verification remain. |
| macOS | Partial | Clean-machine native playback, bundled libmpv dependency closure, installed app/DMG testing, signing, hardened runtime, and notarization remain. |
| Windows | Partial | Embedded video renderer, Windows runtime tests, native DLL bundle, installed EXE/MSI verification, and signing remain. |
| Linux | Partial | Renderer/runtime validation, desktop integration, native dependency bundle, and actual package builds remain. |
| iOS | Not implemented | No iOS target, native playback adapter, persistence adapter, Xcode project, device tests, or release configuration exists. |

Passing compilation or JVM unit tests does not satisfy the native runtime, installation, security, licensing, accessibility, or signing gates.
