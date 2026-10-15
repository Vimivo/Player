# macOS distribution

Vimivo's initial macOS distribution target is a directly distributed application and DMG. A release must bundle a Java runtime and every non-system native playback dependency so users do not need Homebrew, mpv, or Java.

## Current verified state

The development machine is Apple Silicon and has mpv/libmpv 0.41.0 with FFmpeg 9.0.2 installed through Homebrew. The renderer bridge currently links to absolute development paths:

- `/opt/homebrew/opt/mpv/lib/libmpv.2.dylib`
- Homebrew OpenJDK's `libjawt.dylib`

The installed libmpv links to FFmpeg, libass, libplacebo, LuaJIT, Rubber Band, uchardet, zimg, libarchive, libbluray, and other non-system dylibs. Copying only `libmpv` would not create a standalone application. Every redistributed binary needs an architecture and license audit, bundle-relative install names, nested signing, and clean-machine verification.

`./gradlew :desktopApp:packageDmg` was executed on 2026-10-14. Compose's `checkRuntime` rejected the Homebrew JDK because it can produce unreliable packaged runtimes. No alternative JDK is registered with `/usr/libexec/java_home` on this machine. The vendor check was deliberately not disabled.

## Required packaging path

1. Install a supported JDK 17 distribution for packaging, such as Amazon Corretto or JetBrains Runtime, and select it through `JAVA_HOME`.
2. Obtain or reproducibly build a redistributable arm64 libmpv dependency set. Do not treat the current Homebrew Cellar as a release source.
3. Audit the license and architecture of every transitive dylib.
4. Place native libraries under `Vimivo.app/Contents/Frameworks` and the renderer bridge in an appropriate signed bundle location.
5. Rewrite non-system dependency install names to `@rpath` or `@loader_path` references and verify with `otool -L`.
6. Configure the runtime library search path so both JNA and the renderer bridge resolve only bundled release libraries.
7. Build the app image and DMG, then verify on a clean Apple Silicon Mac without Homebrew.
8. Sign the outer app and every nested native binary with Hardened Runtime before notarization. Credentials and signing identities must remain outside source control.

Intel support requires a separately built and tested x86_64 dependency set or verified universal binaries. The current arm64 Homebrew installation provides no evidence of Intel compatibility.

## Native integration delivered

The desktop application installs a buffered AWT open-file handler. Finder events received before the Compose collector is active are retained, file order is preserved, and unavailable paths are ignored. The native menu invokes the same authoritative `MpvPlayerEngine`; it does not create another playback session.

Multiple Finder files are accepted, ordered, and loaded directly into the unified continuous playback queue, preserving order and skipping unavailable paths.

## Release status

No signed, notarized, or clean-machine-verified release exists yet. The generated artifacts must not be described as production-ready until bundled native playback, signing, notarization, Gatekeeper, Finder association, and playback tests pass on a clean system.
