#!/bin/bash
# For each variant (extra mpv options), plays a video 3 times in a 960x467 window and measures the picture with xwd.
Xvfb :7 -screen 0 1280x800x24 >/dev/null 2>&1 & export DISPLAY=:7; sleep 1
measure() { # prints the share of lit pixels in the mpv window (0..1)
  xwd -id "$1" -silent 2>/dev/null | convert xwd:- -colorspace gray -threshold 16% -format '%[fx:mean]' info: 2>/dev/null
}
file=/m/$1; shift
for variant in "$@"; do
  out=""
  for i in 1 2 3; do
    mpv --no-config --terminal=no --vo=gpu --gpu-sw=yes --hwdec=auto-safe --keep-open=yes --geometry=960x467+0+0 \
      --ao=null --audio-display=no $variant "$file" & pid=$!
    sleep 2.5
    w=$(xwininfo -root -tree | grep -i 'mpv' | awk '{print $1}' | head -1)
    out="$out $(measure $w)"
    kill $pid; wait $pid 2>/dev/null
  done
  echo "[$variant] lit:$out"
done
