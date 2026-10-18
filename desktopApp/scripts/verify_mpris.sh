#!/usr/bin/env bash
# Verifies Vimivo's MPRIS 2 service end to end with playerctl, inside a private D-Bus session.
#   verify_mpris.sh <path to the Vimivo launcher, e.g. .../app/Vimivo/bin/Vimivo>
# Needs: dbus (dbus-run-session), xvfb-run, playerctl, ffmpeg.
set -euo pipefail

if [ -z "${DBUS_SESSION_BUS_ADDRESS:-}" ] || [ -z "${VIMIVO_MPRIS_INNER:-}" ]; then
    VIMIVO_MPRIS_INNER=1 exec dbus-run-session -- "$0" "$@"
fi

APP="$1"
WORK="$(mktemp -d)"
MEDIA="$WORK/MPRIS Check.mp3"
ffmpeg -loglevel error -f lavfi -i sine=frequency=440:duration=30 -metadata title="MPRIS Check" -metadata artist="Vimivo CI" "$MEDIA"

xvfb-run -a -s "-screen 0 1280x800x24" "$APP" "$MEDIA" >"$WORK/app.log" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true; rm -rf "$WORK"' EXIT

fail() { echo "MPRIS FAIL: $*"; echo "--- app log ---"; tail -40 "$WORK/app.log"; exit 1; }
# Waits up to $2 seconds for `playerctl -p vimivo $1` to print $3.
await() {
    local query="$1" seconds="$2" expected="$3" actual=""
    for _ in $(seq 1 $((seconds * 4))); do
        actual="$(playerctl -p vimivo $query 2>/dev/null || true)"
        [ "$actual" = "$expected" ] && { echo "MPRIS PASS: $query = $expected"; return 0; }
        sleep 0.25
    done
    fail "$query: expected '$expected', got '$actual'"
}

await status 30 Playing
await "metadata xesam:title" 2 "MPRIS Check"
await "metadata xesam:artist" 2 "Vimivo CI"

playerctl -p vimivo pause
await status 3 Paused
playerctl -p vimivo play
await status 3 Playing

playerctl -p vimivo position 12
position="$(playerctl -p vimivo position)"
awk -v p="$position" 'BEGIN { exit !(p >= 11.5 && p < 16) }' || fail "position after seek to 12 s is $position"
echo "MPRIS PASS: SetPosition -> $position s"

playerctl -p vimivo volume 1.5
await volume 3 1.500000

playerctl -p vimivo play-pause
await status 3 Paused
echo "MPRIS: all checks passed"
