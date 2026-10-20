# V0 audit evidence (2026-10-19)

Raw output behind the evidence register in [VLC_FEATURE_GAP.md](../../VLC_FEATURE_GAP.md). Baseline: `a0262fd` plus
the uncommitted desktop working tree of that day. Home-directory paths are shortened to `~`.

| File | Register ID | Command |
|---|---|---|
| `E1-jvm-tests.log`, `E1-junit-summary.txt` | E1, E2 | `./gradlew :shared:desktopTest --rerun :desktopApp:desktopTest --rerun :androidApp:testDebugUnitTest --rerun --continue` |
| `E3-desktop-smoke.log` | E3 | `./gradlew :desktopApp:run --args="--smoke-test <pack files>"` on the pack from `scripts/generate_test_media.sh` |
| `E4-mpv-aspect-none.txt` | E4 | mpv 0.41.0 CLI, `--video-aspect-override=none` |
| `E5-mpv-hdr-properties.txt` | E5 | mpv 0.41.0 CLI, colour properties of `hdr10.mkv` |
| `E6-mpv-protocols-and-bundle.txt` | E6 | `mpv --list-protocols` and the bundled `Vimivo.app/Contents/Frameworks` listing |
| `E7-ios-xcodebuild-test.log` | E7 | `xcodebuild test -scheme iosApp` on an iPhone simulator (iOS 26.5) |
| `E8-ci-runs.txt` | E8 | `gh run list --limit 20` |

Regenerate the media pack with `scripts/generate_test_media.sh` (output in `testmedia/pack/`, not committed).
