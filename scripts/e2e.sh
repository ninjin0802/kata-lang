#!/usr/bin/env bash
# Builds the examples and checks them on the connected emulator or device (spec 08 #device).
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q installDist
exec python3 scripts/e2e.py "$@"
