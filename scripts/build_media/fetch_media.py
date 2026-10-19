#!/usr/bin/env python3
"""
Downloads Vimivo's prebuilt LGPL media package for one target from our own GitHub Release and verifies it (ADR-004).

    fetch_media.py --target macos-universal|windows-x86_64|linux-x86_64 --dest DIR

The release tag, asset names and SHA-256 checksums come from media.lock next to this script; a package whose
checksum differs is deleted and the script fails. DIR receives the unpacked package (lib/ or bin/, include/mpv,
licenses/, build-info.json). Already-downloaded archives in VIMIVO_MEDIA_CACHE (CI restores it with actions/cache)
are reused after the same checksum check.
"""

import argparse
import hashlib
import json
import os
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCK = json.loads((HERE / "media.lock").read_text())


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--target", choices=sorted(LOCK["assets"]), required=True)
    parser.add_argument("--dest", type=Path, required=True)
    args = parser.parse_args()

    asset = LOCK["assets"][args.target]
    cache = Path(os.environ.get("VIMIVO_MEDIA_CACHE", Path.home() / ".cache" / "vimivo-media-packages"))
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / asset["file"]
    if archive.exists() and sha256(archive) != asset["sha256"]:
        archive.unlink()
    if not archive.exists():
        url = f"https://github.com/{LOCK['repository']}/releases/download/{LOCK['release']}/{asset['file']}"
        print(f"Downloading {url}", flush=True)
        partial = archive.with_suffix(".part")
        with urllib.request.urlopen(url) as response, open(partial, "wb") as out:
            shutil.copyfileobj(response, out)
        partial.rename(archive)
    actual = sha256(archive)
    if actual != asset["sha256"]:
        archive.unlink()
        sys.exit(f"SHA-256 mismatch for {asset['file']}: got {actual}, media.lock pins {asset['sha256']}")
    print(f"Verified {asset['file']} ({actual})")

    destination = args.dest.resolve()
    if destination.exists():
        shutil.rmtree(destination)
    staging = destination.with_name(destination.name + ".unpack")
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    with tarfile.open(archive) as tar:
        tar.extractall(staging, filter="data")
    (root,) = list(staging.iterdir())
    root.rename(destination)
    staging.rmdir()
    print(f"Unpacked into {destination}")


if __name__ == "__main__":
    main()
