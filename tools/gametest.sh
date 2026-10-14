#!/bin/sh
# Runs all GameTests and prints only the summary and failures.
cd "$(dirname "$0")/.." || exit 1
./gradlew runGameTestServer --console=plain > build/gametest.log 2>&1
status=$?
grep -aE "GAME TESTS COMPLETE|required tests (passed|failed)|tests failed|::|failed!|Exception|Caused by|BUILD (SUCCESSFUL|FAILED)|error:" build/gametest.log | grep -v "DEBUG" | head -60
exit $status
