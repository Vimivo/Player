#!/usr/bin/env bash
set -euo pipefail

# Vimivo — Complete Developer & CI Verification Script
# Runs every gate the current host can run, then lists what only CI (or another host) can verify.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

HOST_OS="$(uname -s)"
CI_ONLY=()

echo "================================================================="
echo "  Vimivo — Unified Quality & Build Verification (host: ${HOST_OS} $(uname -m))"
echo "================================================================="
echo "Target modules: :shared, :desktopApp, :androidApp, iosApp"
echo ""

echo "==> Step 1: Running static analysis (Detekt)..."
./gradlew detekt --quiet

echo "==> Step 2: Running shared module tests (:shared:desktopTest)..."
./gradlew :shared:desktopTest --quiet

echo "==> Step 3: Compiling desktop application (:desktopApp:compileKotlinDesktop)..."
./gradlew :desktopApp:compileKotlinDesktop --quiet

echo "==> Step 4: Running desktop test suite (:desktopApp:desktopTest)..."
./gradlew :desktopApp:desktopTest --quiet

echo "==> Step 5: Running Android unit tests (:androidApp:testDebugUnitTest)..."
./gradlew :androidApp:testDebugUnitTest --quiet

echo "==> Step 6: Assembling Android debug package (:androidApp:assembleDebug)..."
./gradlew :androidApp:assembleDebug --quiet

echo "==> Step 7: Desktop smoke test (real window, local video + audio playback)..."
SMOKE_MEDIA=(testmedia/video_landscape.mp4 testmedia/video_sample.mkv testmedia/track2_lunar.mp3 testmedia/track1_solaris.flac)
if [ -f "${SMOKE_MEDIA[0]}" ] && { [ "${HOST_OS}" = "Darwin" ] || [ -n "${DISPLAY:-}" ]; }; then
    smoke_args="--smoke-test"
    for media in "${SMOKE_MEDIA[@]}"; do smoke_args="${smoke_args} ${PROJECT_ROOT}/${media}"; done
    smoke_log="$(mktemp)"
    ./gradlew :desktopApp:run --quiet --args="${smoke_args}" 2>&1 | grep "^VIMIVO_SMOKE" | tee "${smoke_log}" || true
    passed="$(grep -c '^VIMIVO_SMOKE PASS' "${smoke_log}" || true)"
    rm -f "${smoke_log}"
    # Each file, plus the fullscreen-enter, fullscreen-exit and resize picture checks on the first video.
    expected=$(( ${#SMOKE_MEDIA[@]} + 3 ))
    if [ "${passed}" -ne "${expected}" ]; then
        echo "FAILURE: desktop smoke test passed ${passed}/${expected} checks" >&2
        exit 1
    fi
else
    echo "Skipped: needs a display (the test media is checked in under testmedia/)."
    CI_ONLY+=("Desktop smoke test on this host")
fi

if [ "${HOST_OS}" = "Darwin" ] && command -v xcodebuild >/dev/null 2>&1; then
    echo "==> Step 8: Building the iOS shared framework (:shared:linkDebugFrameworkIosSimulatorArm64)..."
    ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet

    echo "==> Step 9: Running iOS unit tests (xcodebuild test)..."
    SIMULATOR="$(xcrun simctl list devices available | grep -E 'iPhone [0-9]+' | head -n 1 | sed -E 's/^[[:space:]]*([^(]+) \(.*/\1/' | sed -E 's/[[:space:]]+$//')"
    if [ -z "${SIMULATOR}" ]; then
        echo "FAILURE: no available iPhone simulator; install one in Xcode > Settings > Components" >&2
        exit 1
    fi
    xcodebuild test -quiet \
        -project iosApp/iosApp.xcodeproj \
        -scheme iosApp \
        -destination "platform=iOS Simulator,name=${SIMULATOR}" \
        CODE_SIGNING_ALLOWED=NO
else
    CI_ONLY+=("iOS framework, Xcode build and unit tests (ios.yml, needs macOS + Xcode)")
fi

echo "==> Step 10: Validating documentation cross-links (./scripts/validate_docs.sh)..."
"${SCRIPT_DIR}/validate_docs.sh"

case "${HOST_OS}" in
    Darwin) CI_ONLY+=("Windows EXE/MSI packaging + packaged smoke test (windows.yml)" "Linux DEB/RPM/AppImage packaging + packaged smoke test (linux.yml)" "macOS x86_64 DMG (macos.yml, macos-15-intel runner)") ;;
    Linux) CI_ONLY+=("macOS DMGs + iOS (macos.yml, ios.yml)" "Windows EXE/MSI packaging + packaged smoke test (windows.yml)") ;;
    *) CI_ONLY+=("macOS DMGs + iOS (macos.yml, ios.yml)" "Linux DEB/RPM/AppImage packaging + packaged smoke test (linux.yml)") ;;
esac
CI_ONLY+=("Android instrumentation tests (need an emulator or device)")

echo ""
echo "================================================================="
echo "  SUCCESS: All verification gates for this host passed."
echo "================================================================="
echo "Not verifiable on this host; confirm in CI or on the named host:"
for item in "${CI_ONLY[@]}"; do echo "  - ${item}"; done
