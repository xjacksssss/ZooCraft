"""Exhibit coverage over a long run (Phase 3 spec sections 1 and 8.3).

Usage: python tools/perf/coverage.py <tag> "<zoo>" <visitors> <minutes> [--sprint]
Sets the per-zoo limit, spawns visitors until 90% are there, writes the exhibit list with /zoo debug perf exhibits at the
start and at the end, and samples /zoo debug perf "<zoo>" once a minute for the stuck count and the longest stuck time.
Prints and appends to tools/perf/results/<tag>.md: exhibits, share viewed during the run, median and maximum views during
the run, the max/median ratio, and the worst stuck time seen.

With --sprint the run's game time passes as fast as the server can tick (/tick sprint), one game minute at a time, so a
20-minute run takes a few real minutes. Coverage and stuck times count game time, so the results mean the same; tick
timings from a sprinted run mean nothing, so measure those with ramp.py.
"""
import csv
import pathlib
import re
import sys
import time

from rcon_client import Rcon
from ramp import RESULTS, REPO, set_limit, visitor_count

EXHIBITS_CSV = REPO / "run-server" / "logs" / "zoocraft-exhibits.csv"


def snapshot(r, zoo):
    r.command(f'zoo debug perf exhibits "{zoo}"')
    time.sleep(1)
    with EXHIBITS_CSV.open() as f:
        return {row["exhibit"]: int(row["views_total"]) for row in csv.DictReader(f)}


def stuck(r, zoo):
    out = r.command(f'zoo debug perf "{zoo}"')
    m = re.search(r"Stuck visitors: (\d+)", out)
    count = int(m.group(1)) if m else 0
    longest = max([int(s) for s in re.findall(r"for (\d+) s", out)] or [0])
    return count, longest


def game_time(r):
    return int(re.search(r"(\d+)", r.command("time query gametime")).group(1))


def main():
    tag, zoo, visitors, minutes = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    RESULTS.mkdir(exist_ok=True)
    r = Rcon()
    set_limit(visitors)
    start = time.time()
    while time.time() - start < 420 and visitor_count(r) < visitors * 0.9:
        for _ in range(10):
            r.command(f'zoo debug spawn "{zoo}"')
        time.sleep(1)
    before = snapshot(r, zoo)
    worst_count, worst_time = 0, 0
    sprint = "--sprint" in sys.argv
    for _ in range(minutes):
        if sprint:
            # Ten game seconds at a time, checking stuck visitors after each, so no long episode slips between samples.
            for _ in range(6):
                until = game_time(r) + 200
                r.command("tick sprint 200")
                while game_time(r) < until:
                    time.sleep(0.5)
                count, longest = stuck(r, zoo)
                worst_count, worst_time = max(worst_count, count), max(worst_time, longest)
        else:
            time.sleep(60)
        count, longest = stuck(r, zoo)
        worst_count, worst_time = max(worst_count, count), max(worst_time, longest)
        print(f"  stuck {count}, longest {longest} s", flush=True)
    after = snapshot(r, zoo)
    deltas = sorted(after[e] - before.get(e, 0) for e in after)
    viewed = sum(1 for d in deltas if d > 0)
    median = deltas[len(deltas) // 2] if deltas else 0
    top = deltas[-1] if deltas else 0
    line = (f"| {visitors} | {minutes} min | {len(deltas)} | {100.0 * viewed / max(1, len(deltas)):.1f}% | {median} | {top} "
            f"| {top / max(1, median):.1f} | {worst_count} | {worst_time} s |")
    out = RESULTS / f"{tag}.md"
    if not out.exists():
        out.write_text("| Visitors | Run | Exhibits | Viewed | Median views | Max views | Max/median | Most stuck | Longest stuck |\n"
                       "|---|---|---|---|---|---|---|---|---|\n")
    with out.open("a") as f:
        f.write(line + "\n")
    print(line, flush=True)


if __name__ == "__main__":
    main()
