#!/bin/bash
# Usage: smoke.sh <media...>  — syncs /src to /work and runs the desktop smoke test under Xvfb.
set -u
rsync -a --delete --exclude build/ --exclude .gradle/ --exclude .kotlin/ --exclude .idea/ --exclude local.properties /src/ /work/
cd /work
export VIMIVO_MPV_LOG_FILE=/out/mpv.log VIMIVO_SMOKE_CAPTURE_DIR=/out/caps; rm -rf /out/caps
args="--smoke-test"; for m in "$@"; do args="$args /work/testmedia/$m"; done
xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew --no-daemon -q :desktopApp:run --args="$args" 2>&1 | grep -E 'VIMIVO_|BadWindow|Exception|^e:' 
