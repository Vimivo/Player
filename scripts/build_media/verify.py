#!/usr/bin/env python3
"""
Verifies a built media package is LGPL-only and has every filter Vimivo needs (ADR-004).

    verify.py <unpacked package dir> [--report report.json]

Checks, from the libraries themselves (not from build logs):
1. every FFmpeg library reports "LGPL version 2.1 or later" and was configured without --enable-gpl,
   --enable-nonfree or --enable-version3;
2. libmpv's own configuration says -Dgpl=false;
3. every lavfi filter Vimivo uses or plans to use (VLC-parity matrix #13–15, #19–21, #24) exists, and the
   GPL-only filters it must not rely on are absent.
Exits non-zero on any violation.
"""

import ctypes
import json
import os
import sys
from pathlib import Path

FFMPEG_LIBRARIES = ["avutil", "avcodec", "avformat", "avfilter", "swscale", "swresample", "avdevice"]
EXPECTED_LICENSE = "LGPL version 2.1 or later"
FORBIDDEN_CONFIGURE_FLAGS = ["--enable-gpl", "--enable-nonfree", "--enable-version3"]

# Filter -> matrix item(s) that use it. Replacements for GPL-only filters are noted.
REQUIRED_FILTERS = {
    # #13 deinterlacing modes (mcdeint and kerndeint are GPL-only; these are LGPL)
    "yadif": "#13", "bwdif": "#13", "w3fdif": "#13", "estdif": "#13",
    # #12 black-bar detection: bbox replaces the GPL-only cropdetect
    "bbox": "#12", "crop": "#12",
    # #14 picture adjustments beyond libmpv's renderer controls (vf eq is GPL-only)
    "colorbalance": "#14", "colorlevels": "#14", "hue": "#14", "curves": "#14", "lutrgb": "#14",
    # #15 effects: gblur/avgblur replace boxblur, nlmeans/atadenoise replace hqdn3d (both GPL-only)
    "unsharp": "#15", "transpose": "#15", "hflip": "#15", "vflip": "#15", "rotate": "#15", "gblur": "#15",
    "avgblur": "#15", "noise": "#15", "edgedetect": "#15", "negate": "#15", "vignette": "#15", "deband": "#15",
    "nlmeans": "#15", "atadenoise": "#15",
    # #19 equalizer, #21 boost limiter
    "equalizer": "#19", "superequalizer": "#19", "volume": "#19 #21", "alimiter": "#21",
    # #20 compressor, widener, spatial effects (sofalizer additionally needs libmysofa, not built)
    "acompressor": "#20", "extrastereo": "#20", "stereowiden": "#20", "haas": "#20", "crossfeed": "#20",
    # #24 normalization
    "dynaudnorm": "#24", "loudnorm": "#24",
    # general
    "scale": "general", "format": "general", "aresample": "general",
}
GPL_ONLY_FILTERS = ["cropdetect", "hqdn3d", "boxblur", "eq", "mcdeint", "kerndeint", "pp7", "spp", "stereo3d"]


def library_path(directory, name):
    """The shared library file for name (libavcodec, mpv) on this platform."""
    candidates = sorted(directory.glob(f"lib{name}.*dylib")) + sorted(directory.glob(f"lib{name}.so.*")) + \
        sorted(directory.glob(f"{name}-*.dll")) + sorted(directory.glob(f"lib{name}-*.dll"))
    regular = [c for c in candidates if not c.is_symlink()] or candidates
    if not regular:
        sys.exit(f"missing library {name} in {directory}")
    return regular[0]


def main():
    package = Path(sys.argv[1]).resolve()
    report_path = Path(sys.argv[sys.argv.index("--report") + 1]) if "--report" in sys.argv else None
    libdir = package / ("bin" if (package / "bin").is_dir() else "lib")
    if os.name == "nt":
        os.add_dll_directory(str(libdir))
    failures = []
    report = {"ffmpeg": {}, "mpv": {}, "filters": {}}

    loaded = {}
    for name in FFMPEG_LIBRARIES:
        library = ctypes.CDLL(str(library_path(libdir, name)))
        loaded[name] = library
        license_fn = getattr(library, f"{name}_license")
        configuration_fn = getattr(library, f"{name}_configuration")
        license_fn.restype = ctypes.c_char_p
        configuration_fn.restype = ctypes.c_char_p
        license_text = license_fn().decode()
        configuration = configuration_fn().decode()
        report["ffmpeg"][name] = {"license": license_text, "configuration": configuration}
        if license_text != EXPECTED_LICENSE:
            failures.append(f"lib{name} license is '{license_text}'")
        for flag in FORBIDDEN_CONFIGURE_FLAGS:
            if flag in configuration.split():
                failures.append(f"lib{name} was configured with {flag}")

    avfilter = loaded["avfilter"]
    avfilter.avfilter_get_by_name.restype = ctypes.c_void_p
    avfilter.avfilter_get_by_name.argtypes = [ctypes.c_char_p]
    for name, items in REQUIRED_FILTERS.items():
        present = bool(avfilter.avfilter_get_by_name(name.encode()))
        report["filters"][name] = {"present": present, "matrix": items}
        if not present:
            failures.append(f"filter {name} ({items}) is missing")
    for name in GPL_ONLY_FILTERS:
        if avfilter.avfilter_get_by_name(name.encode()):
            failures.append(f"GPL-only filter {name} is present")
    report["gpl_only_filters_absent"] = GPL_ONLY_FILTERS

    mpv = ctypes.CDLL(str(library_path(libdir, "mpv")))
    mpv.mpv_create.restype = ctypes.c_void_p
    mpv.mpv_initialize.argtypes = [ctypes.c_void_p]
    mpv.mpv_get_property_string.restype = ctypes.c_void_p
    mpv.mpv_get_property_string.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    mpv.mpv_free.argtypes = [ctypes.c_void_p]
    mpv.mpv_terminate_destroy.argtypes = [ctypes.c_void_p]
    handle = mpv.mpv_create()
    for option, value in (("config", "no"), ("terminal", "no"), ("vo", "null"), ("ao", "null")):
        mpv.mpv_set_option_string(ctypes.c_void_p(handle), option.encode(), value.encode())
    if mpv.mpv_initialize(handle) < 0:
        failures.append("libmpv failed to initialize")
    for prop in ("mpv-version", "mpv-configuration", "ffmpeg-version"):
        pointer = mpv.mpv_get_property_string(handle, prop.encode())
        report["mpv"][prop] = ctypes.string_at(pointer).decode() if pointer else None
        if pointer:
            mpv.mpv_free(pointer)
    mpv.mpv_terminate_destroy(handle)
    configuration = report["mpv"].get("mpv-configuration") or ""
    if "-Dgpl=false" not in configuration:
        failures.append(f"libmpv configuration lacks -Dgpl=false: {configuration}")
    for gpl_feature in ("-Ddvdnav=enabled", "-Dcdda=enabled", "-Drubberband=enabled"):
        if gpl_feature in configuration:
            failures.append(f"libmpv was built with {gpl_feature}")

    report["result"] = "fail" if failures else "pass"
    report["failures"] = failures
    text = json.dumps(report, indent=2)
    if report_path:
        report_path.write_text(text + "\n")
    print(json.dumps({k: report[k] for k in ("result", "failures")}, indent=2))
    for name in FFMPEG_LIBRARIES:
        print(f"lib{name}: {report['ffmpeg'][name]['license']}")
    print(f"libmpv: {report['mpv'].get('mpv-version')} — {configuration}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
