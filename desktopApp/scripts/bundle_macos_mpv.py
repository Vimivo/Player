#!/usr/bin/env python3
"""
bundle_macos_mpv.py

Discovers, copies, rewrites install names, and bundles all native libmpv and
Cocoa bridge dylib dependencies into Vimivo.app/Contents/Frameworks.
Ensures zero runtime dependency on the Homebrew prefix or external tools.

The Homebrew prefix comes from VIMIVO_MPV_PREFIX (/opt/homebrew on Apple Silicon, /usr/local on Intel).
Bundled code is signed with VIMIVO_MAC_SIGN_IDENTITY (a Developer ID, using the hardened runtime and the
given entitlements) when set, and ad-hoc otherwise.
"""

import os
import sys
import shutil
import subprocess

CLT_PATH = "/Library/Developer/CommandLineTools/usr/bin"
OTOOL = os.path.join(CLT_PATH, "otool") if os.path.exists(os.path.join(CLT_PATH, "otool")) else "otool"
INSTALL_NAME_TOOL = os.path.join(CLT_PATH, "install_name_tool") if os.path.exists(os.path.join(CLT_PATH, "install_name_tool")) else "install_name_tool"
HOMEBREW = os.environ.get("VIMIVO_MPV_PREFIX", "/opt/homebrew").rstrip("/")
SIGN_IDENTITY = os.environ.get("VIMIVO_MAC_SIGN_IDENTITY", "").strip() or "-"


def codesign(path, entitlements=None):
    """Ad-hoc signs for local builds; Developer ID signing adds the hardened runtime and a secure timestamp."""
    cmd = ["codesign", "--force", "--sign", SIGN_IDENTITY]
    if SIGN_IDENTITY != "-":
        cmd += ["--options", "runtime", "--timestamp"]
        if entitlements:
            cmd += ["--entitlements", entitlements]
    subprocess.run(cmd + [path], check=True)

def run_cmd(cmd):
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"Command failed ({result.returncode}): {' '.join(cmd)}\nStderr: {result.stderr}")
    return result.stdout

def get_dependencies(dylib_path):
    out = run_cmd([OTOOL, "-L", dylib_path])
    deps = []
    for line in out.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        dep = line.split()[0]
        deps.append(dep)
    return deps

def get_rpaths(binary_path):
    out = run_cmd([OTOOL, "-l", binary_path])
    rpaths = []
    lines = out.splitlines()
    for i, line in enumerate(lines):
        if "cmd LC_RPATH" in line:
            for j in range(i + 1, min(i + 5, len(lines))):
                if "path " in lines[j]:
                    rpath = lines[j].strip().split()[1]
                    rpaths.append(rpath)
                    break
    return rpaths

def add_rpath_if_missing(binary_path, new_rpath):
    existing = get_rpaths(binary_path)
    if new_rpath not in existing:
        subprocess.run([INSTALL_NAME_TOOL, "-add_rpath", new_rpath, binary_path], check=False)

def bundle(app_path, cocoa_bridge_dylib, entitlements=None):
    if not os.path.exists(app_path):
        print(f"Error: app path does not exist: {app_path}", file=sys.stderr)
        sys.exit(1)

    frameworks_dir = os.path.join(app_path, "Contents", "Frameworks")
    os.makedirs(frameworks_dir, exist_ok=True)

    initial_binaries = [
        f"{HOMEBREW}/opt/mpv/lib/libmpv.2.dylib",
        cocoa_bridge_dylib,
    ]

    # Resolve all transitive homebrew dylibs
    discovered = {}
    to_visit = [b for b in initial_binaries if os.path.exists(b)]

    while to_visit:
        curr = to_visit.pop(0)
        real_curr = os.path.realpath(curr)
        basename = os.path.basename(real_curr)
        if basename in discovered:
            continue
        discovered[basename] = real_curr
        deps = get_dependencies(real_curr)
        for dep in deps:
            if dep.startswith(HOMEBREW + "/"):
                real_dep = os.path.realpath(dep)
                dep_name = os.path.basename(real_dep)
                if dep_name not in discovered and real_dep not in to_visit:
                    to_visit.append(real_dep)

    print(f"Discovered {len(discovered)} native libraries to bundle.")

    # Copy files into Frameworks
    bundled_files = {}
    for name, src_path in discovered.items():
        dst_path = os.path.join(frameworks_dir, name)
        shutil.copy2(src_path, dst_path)
        os.chmod(dst_path, 0o755)
        bundled_files[name] = dst_path

    # Copy cocoa bridge if not already in discovered
    cocoa_bridge_name = os.path.basename(cocoa_bridge_dylib)
    if cocoa_bridge_name not in bundled_files and os.path.exists(cocoa_bridge_dylib):
        dst_bridge = os.path.join(frameworks_dir, cocoa_bridge_name)
        shutil.copy2(cocoa_bridge_dylib, dst_bridge)
        os.chmod(dst_bridge, 0o755)
        bundled_files[cocoa_bridge_name] = dst_bridge

    # Symlinks for convenience (e.g. libmpv.dylib -> libmpv.2.dylib)
    symlinks = [
        ("libmpv.dylib", "libmpv.2.dylib"),
        ("libvimivo_mpv_cocoa.dylib", cocoa_bridge_name),
    ]
    for link_name, target in symlinks:
        link_path = os.path.join(frameworks_dir, link_name)
        if not os.path.exists(link_path):
            try:
                # Find matching target in bundled_files
                actual_target = next((f for f in bundled_files.keys() if f.startswith(target.split(".")[0])), target)
                os.symlink(actual_target, link_path)
            except Exception:
                pass

    # Map of original homebrew paths/names to @rpath names
    path_mapping = {}
    for name, real_src in discovered.items():
        path_mapping[real_src] = f"@rpath/{name}"

    # Fix install names and dependencies for all dylibs in Frameworks
    for name, dylib_path in bundled_files.items():
        # Set ID
        subprocess.run([INSTALL_NAME_TOOL, "-id", f"@rpath/{name}", dylib_path], check=False)
        add_rpath_if_missing(dylib_path, "@loader_path")
        add_rpath_if_missing(dylib_path, "@loader_path/../runtime/Contents/Home/lib")

        # Rewrite dependencies
        deps = get_dependencies(dylib_path)
        for dep in deps:
            if dep.startswith(HOMEBREW + "/"):
                real_dep = os.path.realpath(dep) if os.path.exists(dep) else dep
                dep_basename = os.path.basename(real_dep)
                # Find matching bundled library
                target_name = next((b for b in bundled_files.keys() if b.startswith(dep_basename.split(".")[0])), dep_basename)
                new_dep = f"@rpath/{target_name}"
                subprocess.run([INSTALL_NAME_TOOL, "-change", dep, new_dep, dylib_path], check=False)
            elif "libjawt.dylib" in dep:
                subprocess.run([INSTALL_NAME_TOOL, "-change", dep, "@rpath/libjawt.dylib", dylib_path], check=False)

    # Configure main executable rpaths
    main_executable = os.path.join(app_path, "Contents", "MacOS", "Vimivo")
    if os.path.exists(main_executable):
        add_rpath_if_missing(main_executable, "@executable_path/../Frameworks")
        add_rpath_if_missing(main_executable, "@executable_path/../runtime/Contents/Home/lib")
        add_rpath_if_missing(main_executable, "@executable_path/../runtime/Contents/Home/lib/server")

    # Codesign all dylibs and the app bundle
    print("Signing bundled libraries with " + ("an ad-hoc signature" if SIGN_IDENTITY == "-" else "Developer ID") + "...")
    for dylib_path in bundled_files.values():
        if not os.path.islink(dylib_path):
            codesign(dylib_path)

    if os.path.exists(main_executable):
        codesign(main_executable, entitlements)

    # Inside-out signing: --deep is deprecated for distribution and would not apply the entitlements.
    codesign(app_path, entitlements)

    # Verification: ensure no Homebrew dependencies remain in any bundled dylib
    print("Verifying bundled dependencies...")
    homebrew_references = []
    for name, dylib_path in bundled_files.items():
        deps = get_dependencies(dylib_path)
        for dep in deps:
            if dep.startswith(HOMEBREW + "/"):
                homebrew_references.append((name, dep))

    if homebrew_references:
        print("WARNING: Unresolved Homebrew references found:", file=sys.stderr)
        for name, dep in homebrew_references:
            print(f"  {name} -> {dep}", file=sys.stderr)
        sys.exit(1)
    else:
        print(f"Verification SUCCESS: All native libraries bundled with @rpath. Zero {HOMEBREW} references!")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: bundle_macos_mpv.py <path_to_Vimivo.app> <path_to_libvimivo_mpv_cocoa.dylib> [entitlements.plist]")
        sys.exit(1)
    bundle(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
