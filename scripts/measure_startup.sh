#!/usr/bin/env bash
# Measures desktop startup of a packaged Vimivo opening a file: median "window" (player window visible) and
# "firstFrame" (playback position > 0) over N runs, plus every intermediate VIMIVO_STARTUP milestone.
#
# Usage: scripts/measure_startup.sh [--cold] [--runs N] [--binary PATH] FILE...
#   --cold   drop the OS file cache before every run (macOS: `sudo purge`, Linux: drop_caches); needs sudo.
#            Without it, the first run warms the cache and is discarded, so all reported runs are warm.
# Close any running Vimivo first: a second instance forwards its files and exits.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNS=5
COLD=0
case "$(uname -s)" in
    Darwin) BINARY="${ROOT}/desktopApp/build/compose/binaries/main/app/Vimivo.app/Contents/MacOS/Vimivo" ;;
    *) BINARY="${ROOT}/desktopApp/build/compose/binaries/main/app/Vimivo/bin/Vimivo" ;;
esac
FILES=()
while [ $# -gt 0 ]; do
    case "$1" in
        --cold) COLD=1 ;;
        --runs) RUNS="$2"; shift ;;
        --binary) BINARY="$2"; shift ;;
        *) FILES+=("$1") ;;
    esac
    shift
done
[ ${#FILES[@]} -gt 0 ] || { echo "usage: $0 [--cold] [--runs N] [--binary PATH] FILE..." >&2; exit 2; }
[ -x "${BINARY}" ] || { echo "Packaged app not found at ${BINARY}; run ./gradlew :desktopApp:dumpBaseCdsArchive" >&2; exit 2; }
if pgrep -f "${BINARY}" >/dev/null; then echo "Vimivo is already running; quit it first." >&2; exit 2; fi

drop_caches() {
    case "$(uname -s)" in
        Darwin) sudo purge ;;
        Linux) sync && echo 3 | sudo tee /proc/sys/vm/drop_caches >/dev/null ;;
    esac
}

LOG="$(mktemp -d)"
trap 'rm -rf "${LOG}"' EXIT

run_once() { # file, output log
    [ "${COLD}" = 1 ] && drop_caches
    VIMIVO_STARTUP_TRACE=1 "${BINARY}" "$1" >"$2" 2>&1 &
    local pid=$!
    for _ in $(seq 1 300); do
        grep -q "firstFrame=" "$2" && break
        kill -0 "${pid}" 2>/dev/null || break
        sleep 0.1
    done
    kill "${pid}" 2>/dev/null || true
    wait "${pid}" 2>/dev/null || true
    sleep 1
}

echo "load average before: $(uptime | sed 's/.*load averages*: //')"
echo "mode: $([ "${COLD}" = 1 ] && echo cold || echo warm), runs: ${RUNS}, binary: ${BINARY}"
for file in "${FILES[@]}"; do
    [ "${COLD}" = 1 ] || run_once "${file}" "${LOG}/discard.log"
    for i in $(seq 1 "${RUNS}"); do run_once "${file}" "${LOG}/$(basename "${file}").${i}.log"; done
    python3 - "$(basename "${file}")" "${LOG}" "${RUNS}" <<'EOF'
import re, statistics, sys
name, log, runs = sys.argv[1], sys.argv[2], int(sys.argv[3])
samples = {}
order = []
for i in range(1, runs + 1):
    for key, value in re.findall(r"VIMIVO_STARTUP (\w+)=(\d+)", open(f"{log}/{name}.{i}.log").read()):
        if key not in order:
            order.append(key)
        samples.setdefault(key, []).append(int(value))
print(f"\n{name}")
for key in order:
    values = samples[key]
    note = "" if len(values) == runs else f"  (only {len(values)}/{runs} runs)"
    print(f"  {key:<14} median {statistics.median(values):>6.0f} ms   runs {values}{note}")
EOF
done
echo "load average after: $(uptime | sed 's/.*load averages*: //')"
