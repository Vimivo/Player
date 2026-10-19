#!/usr/bin/env python3
"""
bundle_windows_mpv.py

Discovers, copies, and verifies Windows libmpv and dependent DLLs into
the Compose Desktop packaged application directory.
Ensures zero external dependency on system-installed mpv or MSYS2.
"""

import os
import sys
import shutil
import struct

def is_pe_x64(filepath):
    """Inspects PE header to verify 64-bit AMD64 architecture."""
    try:
        with open(filepath, "rb") as f:
            header = f.read(1024)
            if len(header) < 64:
                return False
            if header[0:2] != b"MZ":
                return False
            pe_offset = struct.unpack_from("<I", header, 0x3C)[0]
            if len(header) < pe_offset + 6:
                f.seek(pe_offset)
                pe_header = f.read(6)
            else:
                pe_header = header[pe_offset:pe_offset + 6]
            if pe_header[0:4] != b"PE\x00\x00":
                return False
            machine = struct.unpack_from("<H", pe_header, 4)[0]
            # 0x8664 is IMAGE_FILE_MACHINE_AMD64
            return machine == 0x8664
    except Exception as e:
        print(f"Warning: could not inspect PE header of {filepath}: {e}", file=sys.stderr)
        return False

def copy_media_notices(package_dir, app_dir):
    """The media package's licence texts and component list, shown by About and required by the LGPL."""
    licenses = os.path.join(package_dir, "licenses")
    info = os.path.join(package_dir, "build-info.json")
    if not (os.path.isdir(licenses) and os.path.isfile(info)):
        print("Warning: no licenses/ or build-info.json next to the DLLs; About will not list them.", file=sys.stderr)
        return
    destination = os.path.join(app_dir, "media-licenses")
    shutil.rmtree(destination, ignore_errors=True)
    shutil.copytree(licenses, destination)
    shutil.copy2(info, os.path.join(app_dir, "media-build-info.json"))


def bundle(app_dir, source_dll_dir=None):
    if not os.path.exists(app_dir):
        print(f"Error: target app dir does not exist: {app_dir}", file=sys.stderr)
        sys.exit(1)

    # One copy, next to Vimivo.exe: Windows resolves libmpv's sibling DLLs (FFmpeg, libass, ...) from there.
    target_dirs = [app_dir]

    copied = []
    if source_dll_dir and os.path.exists(source_dll_dir):
        print(f"Copying native DLLs from {source_dll_dir} to {app_dir}...")
        for file in sorted(os.listdir(source_dll_dir)):
            if file.lower().endswith(".dll"):
                shutil.copy2(os.path.join(source_dll_dir, file), os.path.join(app_dir, file))
                copied.append(file)
        print(f"Copied {len(copied)} native DLLs.")
        copy_media_notices(os.path.dirname(os.path.abspath(source_dll_dir)), app_dir)

    # Verification
    candidate_names = ["mpv-2.dll", "libmpv-2.dll", "libmpv.dll", "mpv.dll"]
    found_mpv = None
    for candidate in candidate_names:
        for target_dir in target_dirs:
            candidate_path = os.path.join(target_dir, candidate)
            if os.path.exists(candidate_path):
                found_mpv = candidate_path
                break
        if found_mpv:
            break

    if not found_mpv:
        print("Warning: libmpv DLL was not found in packaged output.", file=sys.stderr)
        print("Candidate names checked: " + ", ".join(candidate_names), file=sys.stderr)
        return False

    print(f"Verified libmpv DLL: {found_mpv}")
    if is_pe_x64(found_mpv):
        print("Verified PE architecture: x64 (AMD64)")
    else:
        print("Warning: PE header does not report standard AMD64 machine type", file=sys.stderr)

    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: bundle_windows_mpv.py <app_dir> [source_dll_dir]")
        sys.exit(1)

    target_app = sys.argv[1]
    src_dir = sys.argv[2] if len(sys.argv) > 2 else None
    success = bundle(target_app, src_dir)
    sys.exit(0 if success else 1)
