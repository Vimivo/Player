#!/usr/bin/env bash
set -euo pipefail

# Vimivo — Build Rust Core Native Libraries for Android (arm64-v8a, x86_64)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

NDK_DIR="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/27.1.12297006}"

if [ ! -d "${NDK_DIR}" ]; then
    # Fallback to any installed NDK under ndk/
    NDK_BASE="${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk"
    if [ -d "${NDK_BASE}" ]; then
        NDK_DIR="$(find "${NDK_BASE}" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n 1)"
    fi
fi

if [ ! -d "${NDK_DIR}" ]; then
    echo "ERROR: Android NDK not found" >&2
    exit 1
fi

echo "==> Building Rust Core for Android using NDK at: ${NDK_DIR}"
ANDROID_NDK_HOME="${NDK_DIR}" cargo ndk -t arm64-v8a -t x86_64 -o androidApp/src/main/jniLibs build -p vimivo-ffi --release

echo "==> Successfully built Android JNI native libraries in androidApp/src/main/jniLibs"
