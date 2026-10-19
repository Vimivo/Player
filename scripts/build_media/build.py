#!/usr/bin/env python3
"""
Builds Vimivo's LGPL-only media stack (libmpv + FFmpeg + dependencies) from pinned sources (ADR-004).

    build.py --target macos   [--out DIR]   # universal arm64 + x86_64 (lipo), on an Apple Silicon Mac
    build.py --target linux   [--out DIR]   # x86_64, inside the pinned build container
    build.py --target windows [--out DIR]   # x86_64, cross-compiled with mingw-w64 on Linux

Every download (sources, build tools, libplacebo's code generators) is checked against manifest.json; anything
else is refused. All libraries are shared, so the LGPL relinking requirement is met by replacing a library file.
The result is DIR/vimivo-media-<release>-<target>.tar.xz with lib/, include/mpv/, licenses/ and build-info.json.
"""

import argparse
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
MANIFEST = json.loads((HERE / "manifest.json").read_text())
CACHE = Path(os.environ.get("VIMIVO_MEDIA_CACHE", Path.home() / ".cache" / "vimivo-media"))
JOBS = str(os.cpu_count() or 4)
DOWNLOAD_ATTEMPTS = 5
# The GCC runtime (GPL-3.0 WITH GCC-exception-3.1) is linked statically on Windows so no libgcc/libstdc++ DLLs ship.
WINDOWS_RUNTIME = "'-static-libgcc', '-static-libstdc++'"


def log(message):
    print(f"==> {message}", flush=True)


def run(cmd, cwd=None, env=None):
    printable = " ".join(str(part) for part in cmd)
    print(f"$ {printable}", flush=True)
    subprocess.run([str(part) for part in cmd], cwd=cwd, env=env, check=True)


def fetch(entry):
    """Downloads entry['url'] once into the cache and refuses it unless the SHA-256 matches the manifest."""
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / f"{entry['name']}-{entry['version']}-{Path(entry['url']).name}"
    if not target.exists():
        log(f"download {entry['name']} {entry['version']}")
        partial = target.with_suffix(target.suffix + ".part")
        for attempt in range(1, DOWNLOAD_ATTEMPTS + 1):
            try:
                with urllib.request.urlopen(entry["url"], timeout=120) as response, open(partial, "wb") as out:
                    shutil.copyfileobj(response, out)
                break
            except OSError as error:  # URLError, timeouts and resets; the checksum below catches bad content
                if attempt == DOWNLOAD_ATTEMPTS:
                    sys.exit(f"download of {entry['url']} failed after {attempt} attempts: {error}")
                log(f"download failed ({error}); retrying in {attempt * 15} s")
                time.sleep(attempt * 15)
        partial.rename(target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != entry["sha256"]:
        target.unlink()
        sys.exit(f"SHA-256 mismatch for {entry['name']}: got {digest}, manifest pins {entry['sha256']}")
    return target


def extract(archive, into):
    """Extracts a source tarball and returns its single top-level directory."""
    if into.exists():
        shutil.rmtree(into)
    into.mkdir(parents=True)
    with tarfile.open(archive) as tar:
        members = tar.getmembers()
        for member in members:
            check_member(archive, member)
        # Members are validated above; "tar" keeps permissions but refuses device files and absolute paths.
        tar.extractall(into, members=members, filter="tar")
    children = [child for child in into.iterdir() if child.is_dir()]
    if len(children) != 1:
        sys.exit(f"{archive.name}: expected one top-level directory, found {len(children)}")
    return children[0]


def check_member(archive, member):
    """Refuses archive members that would land outside the extraction directory (paths or link targets).

    Python 3.10's backported "data" filter wrongly rejects valid relative symlinks (zimg ships some), so the check is
    done here, with the same rules, on every Python version.
    """
    def escapes(path):
        normalized = os.path.normpath(path)
        return os.path.isabs(path) or normalized == ".." or normalized.startswith(".." + os.sep)

    if escapes(member.name):
        sys.exit(f"{archive.name}: member {member.name} is outside the archive root")
    if member.issym() and escapes(os.path.join(os.path.dirname(member.name), member.linkname)):
        sys.exit(f"{archive.name}: symlink {member.name} -> {member.linkname} points outside the archive")
    if member.islnk() and escapes(member.linkname):
        sys.exit(f"{archive.name}: hard link {member.name} -> {member.linkname} points outside the archive")
    if member.isdev():
        sys.exit(f"{archive.name}: device file {member.name}")


class Target:
    """One build: a platform, an architecture and a prefix everything installs into."""

    def __init__(self, system, arch, work):
        self.system = system
        self.arch = arch
        self.name = f"{system}-{arch}"
        self.work = work / self.name
        self.prefix = self.work / "prefix"
        self.host_arch = platform.machine().replace("aarch64", "arm64").replace("AMD64", "x86_64")
        self.cross = system == "windows" or (system == "macos" and arch != self.host_arch)

    # ---- environment -------------------------------------------------------------------------------------------
    def env(self, tools_bin):
        env = dict(os.environ)
        env["PATH"] = f"{tools_bin}{os.pathsep}{env['PATH']}"
        env["PKG_CONFIG_PATH"] = str(self.prefix / "lib" / "pkgconfig")
        env["PKG_CONFIG_LIBDIR"] = str(self.prefix / "lib" / "pkgconfig")
        if self.system == "linux":
            # System libraries (X11, Wayland, EGL, VA-API, PulseAudio, ALSA, PipeWire, fontconfig, OpenSSL) come from
            # the container's development packages and are not shipped.
            env["PKG_CONFIG_LIBDIR"] += os.pathsep + os.pathsep.join(
                ["/usr/lib/x86_64-linux-gnu/pkgconfig", "/usr/share/pkgconfig", "/usr/lib/pkgconfig"]
            )
        flags = f"-I{self.prefix}/include"
        ldflags = f"-L{self.prefix}/lib"
        if self.system == "macos":
            minimum = MANIFEST["macos_deployment_target"]
            flags += f" -arch {self.arch} -mmacosx-version-min={minimum}"
            ldflags += f" -arch {self.arch} -mmacosx-version-min={minimum} -Wl,-headerpad_max_install_names"
            env["MACOSX_DEPLOYMENT_TARGET"] = minimum
        if self.system == "windows":
            ldflags += " -static-libgcc -static-libstdc++"
        env["CFLAGS"] = flags
        env["CXXFLAGS"] = flags
        env["LDFLAGS"] = ldflags
        return env

    def triplet(self):
        return {
            ("macos", "x86_64"): "x86_64-apple-darwin",
            ("macos", "arm64"): "aarch64-apple-darwin",
            ("windows", "x86_64"): "x86_64-w64-mingw32",
            ("linux", "x86_64"): "x86_64-linux-gnu",
        }[(self.system, self.arch)]

    def meson_cross_file(self):
        if not self.cross:
            return []
        path = self.work / "meson-cross.ini"
        if self.system == "macos":
            cpu = "x86_64" if self.arch == "x86_64" else "aarch64"
            arch_args = f"['-arch', '{self.arch}', '-mmacosx-version-min={MANIFEST['macos_deployment_target']}']"
            link_args = arch_args[:-1] + ", '-Wl,-headerpad_max_install_names']"
            path.write_text(
                "[binaries]\n"
                "c = 'clang'\ncpp = 'clang++'\nobjc = 'clang'\nobjcpp = 'clang++'\nar = 'ar'\nstrip = 'strip'\n"
                "pkg-config = 'pkg-config'\n"
                f"[built-in options]\nc_args = {arch_args}\ncpp_args = {arch_args}\nobjc_args = {arch_args}\n"
                f"c_link_args = {link_args}\ncpp_link_args = {link_args}\nobjc_link_args = {link_args}\n"
                f"[host_machine]\nsystem = 'darwin'\ncpu_family = '{cpu}'\ncpu = '{cpu}'\nendian = 'little'\n"
            )
        else:
            prefix = "x86_64-w64-mingw32-"
            path.write_text(
                "[binaries]\n"
                f"c = '{prefix}gcc'\ncpp = '{prefix}g++'\nar = '{prefix}ar'\nstrip = '{prefix}strip'\n"
                f"windres = '{prefix}windres'\ndlltool = '{prefix}dlltool'\npkg-config = 'pkg-config'\n"
                "[built-in options]\n"
                f"c_args = ['-I{self.prefix}/include']\ncpp_args = ['-I{self.prefix}/include']\n"
                f"c_link_args = ['-L{self.prefix}/lib', {WINDOWS_RUNTIME}]\n"
                f"cpp_link_args = ['-L{self.prefix}/lib', {WINDOWS_RUNTIME}]\n"
                "[host_machine]\nsystem = 'windows'\ncpu_family = 'x86_64'\ncpu = 'x86_64'\nendian = 'little'\n"
            )
        return ["--cross-file", path]

    def cmake_args(self):
        args = []
        if self.system == "macos":
            args += [f"-DCMAKE_OSX_ARCHITECTURES={self.arch}",
                     f"-DCMAKE_OSX_DEPLOYMENT_TARGET={MANIFEST['macos_deployment_target']}"]
        if self.system == "windows":
            args += ["-DCMAKE_SYSTEM_NAME=Windows", "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
                     "-DCMAKE_CXX_COMPILER=x86_64-w64-mingw32-g++", "-DCMAKE_RC_COMPILER=x86_64-w64-mingw32-windres",
                     f"-DCMAKE_FIND_ROOT_PATH={self.prefix}"]
        return args

    def platform_options(self, component):
        options = component.get("options", []) + component.get(f"options_{self.system}", [])
        return [o.format(arch=self.arch, macos_min=MANIFEST["macos_deployment_target"]) for o in options]


# ---- builders ----------------------------------------------------------------------------------------------------
def build_meson(target, component, source, env):
    build = source / "_build"
    run(["meson", "setup", build, source, f"--prefix={target.prefix}", "--libdir=lib", "--buildtype=release",
         "-Ddefault_library=shared", "-Db_ndebug=true", *target.meson_cross_file(), *target.platform_options(component)],
        env=env)
    run(["ninja", "-C", build, "-j", JOBS], env=env)
    run(["ninja", "-C", build, "install"], env=env)


def build_cmake(target, component, source, env):
    build = source / "_build"
    run(["cmake", "-S", source, "-B", build, "-G", "Ninja", f"-DCMAKE_INSTALL_PREFIX={target.prefix}",
         "-DCMAKE_INSTALL_LIBDIR=lib", "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=ON",
         "-DCMAKE_POLICY_VERSION_MINIMUM=3.5", *target.cmake_args(), *target.platform_options(component)], env=env)
    run(["cmake", "--build", build, "-j", JOBS], env=env)
    run(["cmake", "--install", build], env=env)


def build_autotools(target, component, source, env):
    if component.get("autoreconf"):
        run(["autoreconf", "-fi"], cwd=source, env=env)
    host = [f"--host={target.triplet()}"] if target.cross else []
    run(["./configure", f"--prefix={target.prefix}", "--libdir=" + str(target.prefix / "lib"), "--enable-shared",
         "--disable-static", *host, *target.platform_options(component)], cwd=source, env=env)
    run(["make", "-j", JOBS], cwd=source, env=env)
    run(["make", "install"], cwd=source, env=env)


def build_ffmpeg(target, component, source, env):
    args = [f"--prefix={target.prefix}", "--enable-shared", "--disable-static",
            f"--extra-cflags={env['CFLAGS']}", f"--extra-ldflags={env['LDFLAGS']}",
            "--pkg-config=pkg-config"]
    if target.system == "macos":
        args += ["--install-name-dir=@rpath", "--target-os=darwin", f"--arch={target.arch}", "--cc=clang"]
        if target.cross:
            args += ["--enable-cross-compile"]
    if target.system == "windows":
        args += ["--target-os=mingw32", "--arch=x86_64", "--cross-prefix=x86_64-w64-mingw32-", "--enable-cross-compile"]
    run(["./configure", *args, *target.platform_options(component)], cwd=source, env=env)
    run(["make", "-j", JOBS], cwd=source, env=env)
    run(["make", "install"], cwd=source, env=env)


BUILDERS = {"meson": build_meson, "cmake": build_cmake, "autotools": build_autotools, "ffmpeg": build_ffmpeg}


# ---- tools -------------------------------------------------------------------------------------------------------
def build_tools(work):
    """pkgconf, autotools and nasm from pinned sources into a host prefix; nothing from a package manager."""
    prefix = work / "tools"
    env = dict(os.environ)
    env["PATH"] = f"{prefix / 'bin'}{os.pathsep}{env['PATH']}"
    for tool in MANIFEST["tools"]:
        stamp = prefix / f".{tool['name']}-{tool['version']}"
        if stamp.exists():
            continue
        source = extract(fetch(tool), work / "tools-src" / tool["name"])
        log(f"tool {tool['name']} {tool['version']}")
        for command in tool.get("bootstrap", []):
            run(command.split(), cwd=source, env=env)
        run(["./configure", f"--prefix={prefix}"], cwd=source, env=env)
        run(["make", "-j", JOBS, *tool.get("make_targets", [])], cwd=source, env=env)
        if "install_bin" in tool:
            (prefix / "bin").mkdir(parents=True, exist_ok=True)
            for binary in tool["install_bin"]:
                shutil.copy2(source / binary, prefix / "bin" / binary)
        else:
            run(["make", "install"], cwd=source, env=env)
        if "alias" in tool:
            alias = prefix / "bin" / tool["alias"]
            alias.unlink(missing_ok=True)
            alias.symlink_to(tool["name"])
        stamp.write_text("ok")
    return prefix / "bin"


def stage(component, source):
    """Places pinned sources a component expects inside its own tree (git submodules / DEPS checkouts upstream)."""
    for dep in component.get("stage", []):
        staged = extract(fetch(dep), source / "_staged_src" / dep["name"])
        destination = source / dep["dir"]
        if destination.exists():
            shutil.rmtree(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(staged), destination)


# ---- one target ----------------------------------------------------------------------------------------------------
def build_target(target, tools_bin):
    target.work.mkdir(parents=True, exist_ok=True)
    target.prefix.mkdir(parents=True, exist_ok=True)
    env = target.env(tools_bin)
    for component in MANIFEST["components"]:
        if target.system not in component.get("platforms", ["macos", "windows", "linux"]):
            continue
        stamp = target.work / f".{component['name']}-{component['version']}"
        if stamp.exists():
            continue
        log(f"{target.name}: {component['name']} {component['version']}")
        source = extract(fetch(component), target.work / "src" / component["name"])
        stage(component, source)
        BUILDERS[component["build"]](target, component, source, env)
        collect_licenses(target, component, source)
        stamp.write_text("ok")


def collect_licenses(target, component, source):
    destination = target.prefix / "licenses" / component["name"]
    destination.mkdir(parents=True, exist_ok=True)
    for name in component["license_files"]:
        path = source / name
        if not path.exists():
            sys.exit(f"{component['name']}: license file {name} is missing from the source")
        shutil.copy2(path, destination / Path(name).name)
    for dep in component.get("stage", []):
        if not dep["shipped"]:
            continue
        dep_destination = destination / dep["name"]
        dep_destination.mkdir(exist_ok=True)
        for name in dep["license_files"]:
            path = source / dep["dir"] / name
            if not path.exists():
                sys.exit(f"{component['name']}/{dep['name']}: license file {name} is missing from the source")
            shutil.copy2(path, dep_destination / Path(name).name)


# ---- macOS universal ---------------------------------------------------------------------------------------------
def universal_macos(slices, out_prefix):
    """Joins per-architecture dylibs with lipo and points every library reference at @rpath."""
    if out_prefix.exists():
        shutil.rmtree(out_prefix)
    first = slices[0].prefix
    shutil.copytree(first / "include", out_prefix / "include")
    shutil.copytree(first / "licenses", out_prefix / "licenses")
    (out_prefix / "lib").mkdir(parents=True)
    for dylib in sorted((first / "lib").glob("*.dylib")):
        target = out_prefix / "lib" / dylib.name
        if dylib.is_symlink():
            os.symlink(os.readlink(dylib), target)
            continue
        run(["lipo", "-create", *[s.prefix / "lib" / dylib.name for s in slices], "-output", target])
    ours = {path.name for path in (out_prefix / "lib").glob("*.dylib")}
    for dylib in sorted((out_prefix / "lib").glob("*.dylib")):
        if dylib.is_symlink():
            continue
        # Keep the install name's own basename (libavcodec.63.dylib, not the file libavcodec.63.1.102.dylib).
        original_id = subprocess.run(["otool", "-D", dylib], capture_output=True, text=True,
                                     check=True).stdout.splitlines()[-1].strip()
        run(["install_name_tool", "-id", f"@rpath/{Path(original_id).name}", dylib])
        # Sibling libraries resolve next to this one, wherever the directory is (app bundle or a dev prefix).
        run(["install_name_tool", "-add_rpath", "@loader_path", dylib])
        listing = subprocess.run(["otool", "-L", dylib], capture_output=True, text=True, check=True).stdout
        for line in listing.splitlines()[1:]:
            dependency = line.strip().split(" ")[0]
            # References to our own libraries (absolute build-prefix paths) become @rpath; system ones stay.
            if Path(dependency).name in ours and not dependency.startswith("@rpath/"):
                run(["install_name_tool", "-change", dependency, f"@rpath/{Path(dependency).name}", dylib])
        run(["codesign", "--force", "--sign", "-", dylib])


# ---- Windows runtime ---------------------------------------------------------------------------------------------
WINDOWS_SYSTEM_DLL_PREFIXES = ("api-ms-win-", "ext-ms-")


def bundle_windows_runtime(bin_dir, licenses):
    """Copies the mingw-w64 runtime DLLs our libraries import (winpthreads) from the toolchain, with its license."""
    while True:
        ours = {path.name.lower() for path in bin_dir.glob("*.dll")}
        missing = set()
        for dll in bin_dir.glob("*.dll"):
            dump = subprocess.run(["x86_64-w64-mingw32-objdump", "-p", dll], capture_output=True, text=True,
                                  check=True).stdout
            for line in dump.splitlines():
                if "DLL Name:" in line:
                    name = line.split("DLL Name:")[1].strip()
                    if name.lower() not in ours and name.lower().startswith(("libwinpthread", "libgcc", "libstdc++")):
                        missing.add(name)
        if not missing:
            return
        for name in sorted(missing):
            found = find_toolchain_file(name)
            if found is None:
                sys.exit(f"runtime DLL {name} not found in the mingw-w64 toolchain")
            shutil.copy2(found, bin_dir / name)
            log(f"bundled mingw-w64 runtime {name} from {found}")
        destination = licenses / "mingw-w64-runtime"
        destination.mkdir(parents=True, exist_ok=True)
        (destination / "NOTICE").write_text(
            "libwinpthread-1.dll is part of the mingw-w64 runtime (winpthreads), MIT and BSD-3-Clause licensed.\n"
            "Source: https://sourceforge.net/projects/mingw-w64/\n\n"
            "libgcc_s_seh-1.dll and libstdc++-6.dll, when present, are GCC runtime libraries, licensed under\n"
            "GPL-3.0-or-later WITH GCC-exception-3.1; the exception allows distributing them with programs under any\n"
            "licence. Source: https://gcc.gnu.org/\n")


def find_toolchain_file(name):
    """A runtime DLL of the mingw-w64 toolchain: next to its import libraries or in the matching bin directory."""
    gcc = "x86_64-w64-mingw32-gcc"
    printed = subprocess.run([gcc, f"-print-file-name={name}"], capture_output=True, text=True, check=True).stdout
    candidates = [Path(printed.strip())]
    search = subprocess.run([gcc, "-print-search-dirs"], capture_output=True, text=True, check=True).stdout
    for line in search.splitlines():
        if line.startswith("libraries: ="):
            for directory in line[len("libraries: ="):].split(os.pathsep):
                candidates += [Path(directory) / name, Path(directory) / ".." / "bin" / name]
    return next((path.resolve() for path in candidates if path.is_absolute() and path.is_file()), None)


# ---- package -----------------------------------------------------------------------------------------------------
def package(prefix, target_name, out):
    keep_include = prefix / "include" / "mpv"
    staging = out / f"vimivo-media-{MANIFEST['release']}-{target_name}"
    if staging.exists():
        shutil.rmtree(staging)
    (staging / "include").mkdir(parents=True)
    shutil.copytree(keep_include, staging / "include" / "mpv")
    shutil.copytree(prefix / "licenses", staging / "licenses")
    libs = staging / ("bin" if target_name.startswith("windows") else "lib")
    libs.mkdir()
    source_libs = prefix / ("bin" if target_name.startswith("windows") else "lib")
    patterns = {"macos": "*.dylib", "linux": "*.so*", "windows": "*.dll"}[target_name.split("-")[0]]
    for library in sorted(source_libs.glob(patterns)):
        destination = libs / library.name
        if library.is_symlink():
            os.symlink(os.readlink(library), destination)
        else:
            shutil.copy2(library, destination)
    system = target_name.split("-")[0]
    if system == "linux":
        # Sibling libraries resolve next to the one loading them, wherever the directory is installed.
        for library in libs.glob("*.so*"):
            if not library.is_symlink():
                run(["patchelf", "--set-rpath", "$ORIGIN", library])
    if system == "windows":
        bundle_windows_runtime(libs, staging / "licenses")
    fields = ("name", "version", "license", "url", "sha256")
    components = []
    for c in MANIFEST["components"]:
        if system not in c.get("platforms", ["macos", "windows", "linux"]):
            continue
        components.append({key: c[key] for key in fields})
        components += [dict({key: d[key] for key in fields}, part_of=c["name"])
                       for d in c.get("stage", []) if d["shipped"]]
    info = {"release": MANIFEST["release"], "target": target_name, "components": components}
    (staging / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    archive = out / f"{staging.name}.tar.xz"
    with tarfile.open(archive, "w:xz") as tar:
        tar.add(staging, arcname=staging.name)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (out / f"{archive.name}.sha256").write_text(f"{digest}  {archive.name}\n")
    log(f"{archive.name} sha256={digest}")
    return staging


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--target", choices=["macos", "linux", "windows"], required=True)
    parser.add_argument("--work", type=Path, default=Path("build-media"))
    parser.add_argument("--out", type=Path, default=Path("build-media/out"))
    args = parser.parse_args()
    work = args.work.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    tools_bin = build_tools(work)
    if args.target == "macos":
        slices = [Target("macos", arch, work) for arch in ("arm64", "x86_64")]
        for target in slices:
            build_target(target, tools_bin)
        universal = work / "macos-universal"
        universal_macos(slices, universal)
        package(universal, "macos-universal", out)
    else:
        target = Target(args.target, "x86_64", work)
        build_target(target, tools_bin)
        package(target.prefix, target.name, out)


if __name__ == "__main__":
    main()
