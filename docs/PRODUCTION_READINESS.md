# Production readiness

No platform currently passes the public-distribution release gate.

| Platform | Technical status | Primary blockers |
|---|---|---|
| Android | Partial | Full current-HEAD build/device regression, release configuration, device playback, policy, and signed AAB verification remain. |
| macOS | Partial | Standalone application (`Vimivo.app`) and installable DMG (`Vimivo-1.0.0.dmg`) with self-contained JVM and 50-dylib native closure are implemented and verified. Public distribution gate remains blocked on Apple Developer ID certificate signing, Hardened Runtime, and notarization. |
| Windows | Partial | Embedded video renderer, secure DLL loading, Windows Credential Manager, desktop shortcuts/drag-and-drop, and standalone packaging (EXE/MSI) configuration are implemented and test-verified. Packaging execution and clean-environment playback verification require a Windows host/runner. Authenticode signing remains blocked on signing credentials. |
| Linux | Partial | Embedded video renderer, MPRIS 2 D-Bus media integration, Freedesktop Secret Service credential store, desktop shortcuts/drag-and-drop, AppImage/Flatpak/DEB/RPM packaging configuration, and Linux CI workflow are implemented and test-verified. Packaging execution and clean-environment playback verification require a Linux host/runner. |
| iOS | Not implemented | No iOS target, native playback adapter, persistence adapter, Xcode project, device tests, or release configuration exists. |

Passing compilation or JVM unit tests does not satisfy the native runtime, installation, security, licensing, accessibility, or signing gates.
