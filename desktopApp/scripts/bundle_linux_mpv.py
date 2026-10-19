#!/usr/bin/env python3
"""
bundle_linux_mpv.py

Discovers, copies, and verifies Linux libmpv and dependent shared objects into
the Compose Desktop packaged application directory.
Ensures zero external dependency on system-installed mpv or development packages.
"""

import os
import sys
import shutil
import struct

def is_elf_x64(filepath):
    """Inspects ELF header to verify 64-bit x86_64 architecture."""
    try:
        with open(filepath, "rb") as f:
            header = f.read(52)
            if len(header) < 52:
                return False
            # Magic: 0x7F 'E' 'L' 'F'
            if header[0:4] != b"\x7fELF":
                return False
            # EI_CLASS: 2 = 64-bit
            if header[4] != 2:
                return False
            # e_machine: 0x3E = EM_X86_64
            machine = struct.unpack_from("<H", header, 18)[0]
            return machine == 0x3E
    except Exception as e:
        print(f"Warning: could not inspect ELF header of {filepath}: {e}", file=sys.stderr)
        return False

def copy_media_notices(package_dir, destination_dir):
    """The media package's licence texts and component list, shown by About and required by the LGPL."""
    licenses = os.path.join(package_dir, "licenses")
    info = os.path.join(package_dir, "build-info.json")
    if not (os.path.isdir(licenses) and os.path.isfile(info)):
        print("Warning: no licenses/ or build-info.json next to the libraries; About will not list them.",
              file=sys.stderr)
        return
    destination = os.path.join(destination_dir, "media-licenses")
    shutil.rmtree(destination, ignore_errors=True)
    shutil.copytree(licenses, destination)
    shutil.copy2(info, os.path.join(destination_dir, "media-build-info.json"))


def bundle(app_dir, source_lib_dir=None):
    if not os.path.exists(app_dir):
        print(f"Error: target app dir does not exist: {app_dir}", file=sys.stderr)
        sys.exit(1)

    # One copy in lib/ (the jpackage install dir the loader searches); the package's libraries find each other
    # through their $ORIGIN rpath. Symlinks (libmpv.so.2 -> libmpv.so.2.x) are kept as symlinks.
    lib_subdir = os.path.join(app_dir, "lib")
    os.makedirs(lib_subdir, exist_ok=True)
    target_dirs = [lib_subdir]

    if not source_lib_dir:
        print("Error: no libmpv directory given (the unpacked media package's lib/).", file=sys.stderr)
        return False

    copied = []
    if os.path.exists(source_lib_dir):
        print(f"Copying native shared libraries from {source_lib_dir} to {lib_subdir}...")
        for file in sorted(os.listdir(source_lib_dir)):
            if ".so" not in file:
                continue
            src = os.path.join(source_lib_dir, file)
            dst = os.path.join(lib_subdir, file)
            if os.path.lexists(dst):
                os.remove(dst)
            if os.path.islink(src):
                os.symlink(os.readlink(src), dst)
            else:
                shutil.copy2(src, dst)
            copied.append(file)
        print(f"Copied {len(copied)} native shared libraries.")
        copy_media_notices(os.path.dirname(os.path.abspath(source_lib_dir)), lib_subdir)

    # Verification
    candidate_names = ["libmpv.so.2", "libmpv.so.1", "libmpv.so"]
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
        print("Warning: libmpv shared library was not found in packaged output.", file=sys.stderr)
        print("Candidate names checked: " + ", ".join(candidate_names), file=sys.stderr)
        return False

    print(f"Verified libmpv library: {found_mpv}")
    if is_elf_x64(found_mpv):
        print("Verified ELF architecture: x86_64 (EM_X86_64, 64-bit)")
    else:
        print("Warning: ELF header does not report standard x86_64 machine type", file=sys.stderr)

    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: bundle_linux_mpv.py <app_dir> [source_lib_dir]")
        sys.exit(1)

    target_app = sys.argv[1]
    src_dir = sys.argv[2] if len(sys.argv) > 2 else None
    success = bundle(target_app, src_dir)
    sys.exit(0 if success else 1)
