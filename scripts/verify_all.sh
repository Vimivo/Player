#!/usr/bin/env bash
set -euo pipefail

# Vimivo — Complete Developer & CI Verification Script
# Executes the authoritative quality gate across all active modules.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo "================================================================="
echo "  Vimivo — Unified Quality & Build Verification"
echo "================================================================="
echo "Target modules: :shared, :desktopApp, :androidApp"
echo "Quality tools: Detekt, JUnit 5 / Kotlin Test, AGP Unit Tests"
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

echo ""
echo "================================================================="
echo "  SUCCESS: All verification gates passed with zero errors!"
echo "================================================================="
