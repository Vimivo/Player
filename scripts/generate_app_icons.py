#!/usr/bin/env python3
"""
Vimivo Multiplatform App Icon & Logo Generator
Derives all official application icons from docs/brand/vimivo-icon-source.png:
- iOS AppIcon (1024x1024, RGB no alpha, App Store compliant)
- macOS icon.icns (Retina 16 to 1024 via iconutil)
- Desktop Compose Window icon (512x512)
- Linux icons (512x512 and hicolor theme sizes 16 to 512)
- Windows icon.ico (embedded PNGs at 16, 20, 24, 32, 40, 48, 64, 128, 256 — every size Windows shell surfaces request)
- Android legacy mipmaps (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
"""

import os
import shutil
import struct
import subprocess
import tempfile

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_ICON = os.path.join(PROJECT_ROOT, "docs", "brand", "vimivo-icon-source.png")

def run(cmd):
    subprocess.check_call(cmd, shell=True)

def resize_image(src, dst, width, height):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    run(f'sips -z {height} {width} "{src}" --out "{dst}" > /dev/null')

def generate_ios_icon():
    print("==> Generating iOS AppIcon...")
    dst = os.path.join(
        PROJECT_ROOT,
        "iosApp",
        "iosApp",
        "Resources",
        "Assets.xcassets",
        "AppIcon.appiconset",
        "icon_1024.png",
    )
    resize_image(SOURCE_ICON, dst, 1024, 1024)

def generate_macos_icns():
    print("==> Generating macOS icon.icns...")
    dst = os.path.join(
        PROJECT_ROOT,
        "desktopApp",
        "src",
        "desktopMain",
        "resources",
        "macos",
        "icon.icns",
    )
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmpdir:
        iconset = os.path.join(tmpdir, "vimivo.iconset")
        os.makedirs(iconset)
        specs = [
            ("icon_16x16.png", 16, 16),
            ("icon_16x16@2x.png", 32, 32),
            ("icon_32x32.png", 32, 32),
            ("icon_32x32@2x.png", 64, 64),
            ("icon_128x128.png", 128, 128),
            ("icon_128x128@2x.png", 256, 256),
            ("icon_256x256.png", 256, 256),
            ("icon_256x256@2x.png", 512, 512),
            ("icon_512x512.png", 512, 512),
            ("icon_512x512@2x.png", 1024, 1024),
        ]
        for name, w, h in specs:
            target = os.path.join(iconset, name)
            resize_image(SOURCE_ICON, target, w, h)
        run(f'iconutil -c icns "{iconset}" -o "{dst}"')

def generate_desktop_window_icon():
    print("==> Generating Desktop Window icon...")
    dst = os.path.join(
        PROJECT_ROOT,
        "desktopApp",
        "src",
        "desktopMain",
        "resources",
        "icon.png",
    )
    resize_image(SOURCE_ICON, dst, 512, 512)

def generate_linux_icons():
    print("==> Generating Linux icons...")
    # Main 512x512 icon
    main_dst = os.path.join(
        PROJECT_ROOT,
        "desktopApp",
        "src",
        "desktopMain",
        "resources",
        "linux",
        "icon.png",
    )
    resize_image(SOURCE_ICON, main_dst, 512, 512)

    # Hicolor theme icons
    hicolor_dir = os.path.join(
        PROJECT_ROOT,
        "desktopApp",
        "src",
        "desktopMain",
        "resources",
        "linux",
        "icons",
        "hicolor",
    )
    sizes = [16, 32, 48, 64, 128, 256, 512]
    for s in sizes:
        dst = os.path.join(hicolor_dir, f"{s}x{s}", "apps", "vimivo.png")
        resize_image(SOURCE_ICON, dst, s, s)

def generate_windows_ico():
    print("==> Generating Windows icon.ico...")
    dst = os.path.join(
        PROJECT_ROOT,
        "desktopApp",
        "src",
        "desktopMain",
        "resources",
        "windows",
        "icon.ico",
    )
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    with tempfile.TemporaryDirectory() as tmpdir:
        blobs = []
        for s in sizes:
            temp_png = os.path.join(tmpdir, f"icon_{s}.png")
            resize_image(SOURCE_ICON, temp_png, s, s)
            with open(temp_png, "rb") as f:
                blobs.append((s, s, f.read()))

        # Write ICO format containing PNG frames
        num = len(blobs)
        header = struct.pack("<HHH", 0, 1, num)
        entries = []
        offset = 6 + 16 * num
        data_parts = []
        for w, h, data in blobs:
            b_w = 0 if w == 256 else w
            b_h = 0 if h == 256 else h
            size = len(data)
            entry = struct.pack("<BBBBHHII", b_w, b_h, 0, 0, 1, 32, size, offset)
            entries.append(entry)
            data_parts.append(data)
            offset += size

        with open(dst, "wb") as f:
            f.write(header)
            for e in entries:
                f.write(e)
            for d in data_parts:
                f.write(d)

def generate_android_mipmaps():
    print("==> Generating Android mipmap fallback icons...")
    base_res = os.path.join(PROJECT_ROOT, "androidApp", "src", "main", "res")
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        dir_path = os.path.join(base_res, folder)
        os.makedirs(dir_path, exist_ok=True)
        # Standard launcher icon
        launcher_dst = os.path.join(dir_path, "ic_launcher.png")
        resize_image(SOURCE_ICON, launcher_dst, size, size)
        # Round launcher icon
        round_dst = os.path.join(dir_path, "ic_launcher_round.png")
        resize_image(SOURCE_ICON, round_dst, size, size)

def main():
    if not os.path.exists(SOURCE_ICON):
        raise FileNotFoundError(f"Source icon not found at {SOURCE_ICON}")
    print(f"Source icon: {SOURCE_ICON}")
    generate_ios_icon()
    generate_macos_icns()
    generate_desktop_window_icon()
    generate_linux_icons()
    generate_windows_ico()
    generate_android_mipmaps()
    print("All multiplatform application icons successfully generated!")

if __name__ == "__main__":
    main()
