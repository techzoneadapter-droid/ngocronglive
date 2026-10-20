#!/usr/bin/env bash
set -euo pipefail
touch /opt/nrolive-beta/local-data/stop.request
for attempt in $(seq 1 60); do
    kill -0 "$1" 2>/dev/null || exit 0
    sleep 1
done
echo 'Graceful game stop timed out; inspect server logs.' >&2
exit 1
