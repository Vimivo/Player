#!/usr/bin/env bash
set -euo pipefail

# Vimivo — Build Rust Core XCFramework for iOS and macOS
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo "==> Building Rust Core for iOS Simulator (aarch64-apple-ios-sim)..."
cargo build -p vimivo-ffi --target aarch64-apple-ios-sim --release

echo "==> Building Rust Core for iOS Device (aarch64-apple-ios)..."
cargo build -p vimivo-ffi --target aarch64-apple-ios --release

echo "==> Building Rust Core for Mac Catalyst (aarch64-apple-ios-macabi)..."
cargo build -p vimivo-ffi --target aarch64-apple-ios-macabi --release

echo "==> Packaging VimivoCoreRustFFI.xcframework..."
mkdir -p target/xcframework-headers
cp iosApp/VimivoCoreRust/vimivo_ffiFFI.h target/xcframework-headers/
cp iosApp/VimivoCoreRust/vimivo_ffiFFI.modulemap target/xcframework-headers/module.modulemap

mkdir -p iosApp/Frameworks
rm -rf iosApp/Frameworks/VimivoCoreRustFFI.xcframework

xcodebuild -create-xcframework \
  -library target/aarch64-apple-ios-sim/release/libvimivo_ffi.a -headers target/xcframework-headers \
  -library target/aarch64-apple-ios/release/libvimivo_ffi.a -headers target/xcframework-headers \
  -library target/aarch64-apple-ios-macabi/release/libvimivo_ffi.a -headers target/xcframework-headers \
  -output iosApp/Frameworks/VimivoCoreRustFFI.xcframework

echo "==> Successfully created iosApp/Frameworks/VimivoCoreRustFFI.xcframework"
