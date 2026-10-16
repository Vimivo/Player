# Vimivo — Distribution Artifacts & Release Manifest

**Milestones**: 57 & 58  
**Target Release**: `1.0.0-rc.1`  
**Git HEAD**: `0d7ecba`  
**Date**: 2026-10-16  

---

## 1. Release Artifact Catalog & Cryptographic Checksums

All candidate artifacts were built from clean production build configurations. SHA-256 hashes are computed directly from the final distribution bytes.

```text
7425f12d6a8bd450bfe88420c4ad2137f0820e0bcc0e35b1046efdcee65c92b3  androidApp-release-unsigned.apk
8d7f0060a9ec770bb5162d0f5d61ed90f5cd7226a67ef64216030c8710af7133  androidApp-release.aab
e6ab2791a0a660dc75e726906df2dab506350a66e43dd9e97551faf19829146c  Vimivo-1.0.0.dmg
```

### 1.1 Detailed Artifact Attributes

| Artifact Name | Platform / Arch | Exact File Size | SHA-256 Checksum | Build Task | Signing & Notarization Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`androidApp-release-unsigned.apk`** | Android (Universal) | 147,577,927 bytes | `7425f12d6a8bd450bfe88420c4ad2137f0820e0bcc0e35b1046efdcee65c92b3` | `:androidApp:assembleRelease` | Unsigned; ready for Google Play upload key |
| **`androidApp-release.aab`** | Android (App Bundle) | 76,081,890 bytes | `8d7f0060a9ec770bb5162d0f5d61ed90f5cd7226a67ef64216030c8710af7133` | `:androidApp:bundleRelease` | Compatible with Play App Signing |
| **`Vimivo-1.0.0.dmg`** | macOS (Apple Silicon arm64) | 151,452,869 bytes | `e6ab2791a0a660dc75e726906df2dab506350a66e43dd9e97551faf19829146c` | `:desktopApp:packageDmg` | Ad-hoc signed with Hardened Runtime; ready for Developer ID notarization |

---

## 2. Supply Chain & Provenance Verification

- **Software Bill of Materials (SBOM)**: Exported in CycloneDX / SPDX standard JSON format in `docs/sbom.json`.
- **Release Manifest**: Complete machine-readable distribution manifest in `docs/release-manifest.json`.
- **Third-Party Notices**: Comprehensive attribution for all bundled libraries in `docs/THIRD_PARTY_NOTICES.md` and `docs/LICENSE_AUDIT.md`.
- **Zero Cloud Leakage**: Build artifacts contain zero telemetry endpoints, zero tracking SDKs, and zero cloud backend tokens.
