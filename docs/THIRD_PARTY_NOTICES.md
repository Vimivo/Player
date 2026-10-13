# Third-party notices

This inventory is engineering documentation, not legal advice. The shipped artifact's generated dependency inventory controls if it differs from this summary. Release packaging must include complete notices and license texts required by each dependency.

## AndroidX Media3 and AndroidX

- Coordinate: `androidx.media3:*:1.11.1` plus AndroidX Activity, Core, Lifecycle, DataStore, Navigation, Room, DocumentFile, and Compose libraries pinned in the version catalog.
- License: Apache License 2.0.
- Source and license: <https://github.com/androidx/androidx>.

## Kotlin and application libraries

- Kotlin 2.3.20, Compose Multiplatform 1.9.3, kotlinx.coroutines 1.10.2, kotlinx.serialization 1.9.0, and Koin 4.2.2.
- License: Apache License 2.0 according to their published metadata.
- Preserve the copyright notices, license text, and any packaged NOTICE files in release attribution.

## LibVLC Android

- Coordinate: `org.videolan.android:libvlc-all:3.7.7`.
- Publisher: VideoLAN.
- License declared by the published POM: GNU Lesser General Public License, version 2.1.
- Source: <https://code.videolan.org/videolan/libvlcjni>.
- License: <https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>.
- Legal information: <https://www.videolan.org/legal.html>.

Vimivo uses the officially distributed binary AAR and does not copy VLC for Android application source code. A public release must satisfy LGPL 2.1 requirements, including license/copyright notices, Corresponding Source, applicable build information, and a legally reviewed means for replacing or relinking the LGPL-covered component.

Before distribution, bundle the complete license texts and generated notices rather than relying only on this summary. Add the exact-release source location and replacement instructions here and to the public project site.
