# Vimivo media stack (libmpv + FFmpeg), LGPL-only

These scripts build the native playback libraries that the macOS, Windows and Linux apps ship. Each build uses
only the sources pinned in `manifest.json`, by version and SHA-256. Background: ADR-004
(`docs/adr/ADR-004-LGPL-MEDIA-STACK.md`).

| File | Purpose |
|---|---|
| `manifest.json` | Every source, build tool and option. Change versions here only. |
| `build.py` | Builds one target into `vimivo-media-<release>-<target>.tar.xz` plus a `.sha256` file. |
| `verify.py` | Fails unless the libraries are LGPL and every required filter is present (see below). |
| `requirements-build.txt` | Hash-pinned meson, ninja and cmake. |
| `media.lock` | The published packages (release tag, file, SHA-256) that the app workflows download. |
| `fetch_media.py` | Downloads one package from our GitHub Release, checks its SHA-256 and unpacks it. |

`verify.py` checks three things:

- Every FFmpeg library reports "LGPL version 2.1 or later".
- libmpv reports `-Dgpl=false`.
- Every filter the VLC-parity matrix needs is present.

## Building

```sh
python3 -m venv .venv && .venv/bin/pip install --require-hashes -r scripts/build_media/requirements-build.txt
PATH=$PWD/.venv/bin:$PATH python3 scripts/build_media/build.py --target macos --out out   # Apple Silicon Mac
python3 scripts/build_media/verify.py out/vimivo-media-*-macos-universal
```

### Requirements by target

| Target | Build host | Toolchain and libraries |
|---|---|---|
| `macos` | Apple Silicon Mac | Xcode Command Line Tools (clang, swiftc). Produces arm64 + x86_64 slices joined with `lipo`. |
| `linux` | Ubuntu 22.04 container | Needs the distribution's X11, EGL, VA-API, PulseAudio, ALSA, fontconfig and OpenSSL development packages. These libraries are not shipped. |
| `windows` | Ubuntu, cross-compiling with mingw-w64 (POSIX threads) | Uses `.github/workflows/build-media.yml`. |

The `build-media.yml` workflow builds all three targets and verifies each one on its own OS.

## Releasing a new package set

1. Bump `release` and the pinned versions in `manifest.json`, then run the workflow.
2. Attach the verified archives and their `.sha256` files to a GitHub Release named after `release`.
3. Update `media.lock`.

Nothing in CI downloads media libraries from anywhere else.

## Relinking (LGPL)

Every library is a shared library:

- macOS: `@rpath` install names.
- Linux: `$ORIGIN` rpath.
- Windows: DLLs next to `Vimivo.exe`.

To use a modified library, rebuild it from these scripts and replace the file inside the installed app.
