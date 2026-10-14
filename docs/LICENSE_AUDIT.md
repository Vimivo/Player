# Dependency and native-library license audit

Audit date: 2026-10-13. Versions are those pinned in `gradle/libs.versions.toml` and resolved by the release project. This engineering audit is not legal advice.

| Dependency group | Version | License | Release obligations |
|---|---:|---|---|
| Android Gradle Plugin / AndroidX / Media3 / Room / Compose UI test | pinned catalog versions | Apache License 2.0 | Preserve notices and license text; state modifications where required. |
| Kotlin / Compose Multiplatform / kotlinx.coroutines / serialization | 2.3.20 / 1.9.3 / 1.10.2 / 1.9.0 | Apache License 2.0 | Preserve copyright, notice, and license text. |
| Koin | 4.2.2 | Apache License 2.0 | Preserve copyright, notice, and license text. |
| Detekt | 1.23.8 | Apache License 2.0 | Build-time only; retain repository attribution. |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 | Test-only; not packaged in the release runtime. |
| LibVLC Android (`libvlc-all`) | 3.7.7 | GNU LGPL 2.1 (published POM) | Include LGPL text and notices; allow replacement/relinking of the LGPL component; provide Corresponding Source, including applicable build scripts and modifications, by a compliant method. Preserve third-party codec notices. |
| Jaudiotagger (`net.jthink:jaudiotagger`) | 3.0.1 | LGPL (published POM; project source headers describe LGPL 2.1-or-later) | Bundle the applicable LGPL text and attribution, keep the library replaceable as a distinct JAR, and publish modifications to the library if Vimivo makes any. Vimivo currently consumes it unmodified. |

## Native contents

The debug APK inspection found `libvlc.so`, `libvlcjni.so`, `libc++_shared.so`, AndroidX graphics, and DataStore native libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`; no x86 binary was packaged. The same audit must be repeated on the final AAB because Play splits delivery by ABI.

LibVLC is the material licensing risk. Its Maven POM declares LGPL 2.1 and links to the VideoLAN source project. Before release:

1. Archive the exact LibVLC 3.7.7 source and build inputs corresponding to the shipped binary.
2. Publish a durable source/download page or make a legally reviewed written offer, as applicable.
3. Provide installation/relinking information sufficient for recipients to replace the LGPL component in the packaged app; R8 rules must not frustrate that right.
4. Include the full LGPL 2.1 license and all notices from the AAR/source distribution in the app or an easily accessible accompanying location.
5. Review every codec/plugin included by the selected LibVLC artifact for patent, export, and regional distribution constraints.

The [VideoLAN legal page](https://www.videolan.org/legal.html), the artifact's published POM, and the corresponding source distribution are authoritative inputs. Do not infer that VLC application GPL terms apply to Vimivo merely because it uses LibVLC, and do not claim compliance until the replacement/source-delivery process is tested.

## Compatibility conclusion

No known license in the declared application runtime is inherently incompatible with a free Play Store distribution. **Release remains blocked on completing and legally reviewing LGPL compliance materials for the exact LibVLC binary.** Generate a machine-readable dependency/SBOM report from the final release graph and re-run vulnerability and Play SDK Index checks immediately before upload.

