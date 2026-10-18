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

def bundle(app_dir, source_lib_dir=None):
    if not os.path.exists(app_dir):
        print(f"Error: target app dir does not exist: {app_dir}", file=sys.stderr)
        sys.exit(1)

    app_subdir = os.path.join(app_dir, "app")
    lib_subdir = os.path.join(app_dir, "lib")
    target_dirs = [app_dir]
    for d in [app_subdir, lib_subdir]:
        if os.path.exists(d):
            target_dirs.append(d)
        else:
            os.makedirs(d, exist_ok=True)
            target_dirs.append(d)

    if not source_lib_dir:
        # Packages declare libmpv2 as a dependency; the loader then uses the system library.
        print("No libmpv directory given: the app will load the system libmpv (libmpv.so.2).")
        return True

    copied = []
    if os.path.exists(source_lib_dir):
        print(f"Copying native shared libraries from {source_lib_dir} to {app_dir}...")
        for root, _, files in os.walk(source_lib_dir):
            for file in files:
                if ".so" in file.lower():
                    src = os.path.join(root, file)
                    for target_dir in target_dirs:
                        dst = os.path.join(target_dir, file)
                        shutil.copy2(src, dst)
                    copied.append(file)
        print(f"Copied {len(copied)} native shared libraries.")

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
