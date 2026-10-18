#!/usr/bin/env bash
set -euo pipefail

# Recreates the format test pack used by the VLC-parity audit (docs/evidence/V0) in testmedia/pack/.
# The pack is generated, not committed (.gitignore). Needs ffmpeg with libx264, libx265, libvpx, libsvtav1, libopus,
# libmp3lame (Homebrew's ffmpeg has them). Usage: scripts/generate_test_media.sh [output-dir]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${SCRIPT_DIR}/../testmedia/pack}"
mkdir -p "${OUT}"
OUT="$(cd "${OUT}" && pwd)"

command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg is required" >&2; exit 1; }

ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }
video() { echo "-f lavfi -i testsrc2=s=$1:r=$2:d=6"; }
tone() { echo "-f lavfi -i sine=f=${1:-440}:d=6"; }

cd "${OUT}"
echo "Writing test pack to ${OUT}"

# shellcheck disable=SC2046 # the helpers intentionally expand to several arguments
ff $(video 1280x720 30) $(tone) -c:v libx264 -c:a aac h264.mp4
ff $(video 640x360 30) $(tone) -c:v libvpx-vp9 -b:v 300k -c:a libopus vp9.webm
ff $(video 640x360 30) -c:v libsvtav1 -preset 12 -svtav1-params verbosity=0 av1.mkv
ff $(video 1280x720 24) -c:v libx265 -pix_fmt yuv420p10le \
    -x265-params "log-level=error:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:hdr10=1" hdr10.mkv
ff $(video 720x576 25) $(tone) -vf interlace -flags +ilme+ildct -c:v mpeg2video -c:a mp2 interlaced.ts
ff $(video 640x360 25) $(tone) -c:v mpeg4 -c:a libmp3lame -f avi good.avi
ff $(video 640x360 25) $(tone 300) $(tone 500) $(tone 700) -map 0 -map 1 -map 2 -map 3 -c:v libx264 -c:a aac \
    -metadata:s:a:0 language=eng -metadata:s:a:1 language=ben -metadata:s:a:2 language=jpn multiaudio.mkv
ff $(tone) -c:a ac3 ac3.ac3
ff $(tone) -ar 96000 -sample_fmt s32 -c:a flac hires.flac
ff $(video 640x360 25) -c:v flv1 flv.flv
ff $(tone) -c:a libopus opus.ogg
ff $(video 640x360 25) -c:v mjpeg mov.mov

# Damaged variants: an AVI without its idx1 index, and an MP4 cut to 60 % (its moov atom is at the end, so it is lost).
python3 -I - good.avi broken_index.avi <<'PY'
import sys
data = open(sys.argv[1], "rb").read()
index = data.rfind(b"idx1")
if index < 0:
    sys.exit("good.avi has no idx1 chunk")
open(sys.argv[2], "wb").write(data[:index])
PY
size="$(wc -c < h264.mp4 | tr -d ' ')"
head -c $((size * 6 / 10)) h264.mp4 > truncated.mp4

ls -1 "${OUT}"
