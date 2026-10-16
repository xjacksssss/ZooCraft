#!/usr/bin/env bash
# Runs the recognition audit in ATM10 without anyone at the keyboard, then checks it against the expectations.
# Usage: tools/atm10/recognition-audit.sh [save] [--accept]
# The save (default zootest) must exist in run-atm10/saves. Run tools/sync-atm10-mods.ps1 first after an ATM10 update.
# The game opens the save, spawns every mob in a temporary zoo 2048 blocks east of spawn, writes
# run-atm10/logs/zoocraft-recognition-audit.csv and .txt, and closes itself.
set -euo pipefail
cd "$(dirname "$0")/../.."
SAVE="${1:-zootest}"
shift || true
RESULTS=run-atm10/logs/zoocraft-recognition-audit.csv
rm -f "$RESULTS" run-atm10/logs/zoocraft-recognition-audit.txt
./gradlew runAtm10Client -Patm10 -PquickPlay="$SAVE" -PrecognitionAudit
if [ ! -f "$RESULTS" ]; then
    echo "The audit wrote no results; see run-atm10/logs/latest.log" >&2
    exit 2
fi
cat run-atm10/logs/zoocraft-recognition-audit.txt
python tools/atm10/check_recognition.py "$RESULTS" "$@"
