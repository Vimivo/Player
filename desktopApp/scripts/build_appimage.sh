#!/usr/bin/env bash
set -euo pipefail

# build_appimage.sh
# Packages a self-contained Vimivo AppImage from createDistributable output.

APP_DIR="${1:-desktopApp/build/compose/binaries/main/app/Vimivo}"
OUTPUT_DIR="${2:-desktopApp/build/compose/binaries/main/appimage}"
APPDIR_STAGING="${OUTPUT_DIR}/Vimivo.AppDir"

if [ ! -d "$APP_DIR" ]; then
    echo "Error: App directory does not exist: $APP_DIR" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
rm -rf "$APPDIR_STAGING"
mkdir -p "$APPDIR_STAGING"

echo "Assembling AppDir structure in $APPDIR_STAGING..."
cp -r "$APP_DIR"/* "$APPDIR_STAGING/"

# Copy desktop and icon metadata
cp desktopApp/src/desktopMain/resources/linux/dev.vimivo.desktop.desktop "$APPDIR_STAGING/"
cp desktopApp/src/desktopMain/resources/linux/icon.png "$APPDIR_STAGING/vimivo.png"
cp desktopApp/src/desktopMain/resources/linux/icon.png "$APPDIR_STAGING/.DirIcon"

# Create standard AppRun launcher
cat << 'EOF' > "$APPDIR_STAGING/AppRun"
#!/bin/sh
HERE="$(dirname "$(readlink -f "${0}")")"
export PATH="${HERE}/bin:${PATH}"
export LD_LIBRARY_PATH="${HERE}/lib:${HERE}/app:${LD_LIBRARY_PATH}"
exec "${HERE}/bin/Vimivo" "$@"
EOF
chmod +x "$APPDIR_STAGING/AppRun"

echo "AppDir assembled successfully."

# If appimagetool is available, package the AppImage
if command -v appimagetool >/dev/null 2>&1; then
    echo "Building AppImage using system appimagetool..."
    appimagetool "$APPDIR_STAGING" "$OUTPUT_DIR/Vimivo-1.0.0-x86_64.AppImage"
    echo "AppImage created at $OUTPUT_DIR/Vimivo-1.0.0-x86_64.AppImage"
else
    echo "Notice: appimagetool not found in PATH. AppDir staging preserved at $APPDIR_STAGING."
fi
