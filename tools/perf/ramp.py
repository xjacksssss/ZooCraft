"""Steps the per-zoo visitor limit and records one minute per step (Phase 3 spec section 6.4).

Usage: python tools/perf/ramp.py <tag> "<zoo1,zoo2,...>" <levels e.g. 0,12,25,50,100,150,200> [--jfr <server pid>]
For each level: set maxVisitorsPerZoo, spawn groups with /zoo debug spawn until 90% are there (at most 7 minutes), settle for
30 s, then log a minute with /zoo debug perf log 60 while sampling /tick query, and append a results line to
tools/perf/results/<tag>.md. With --jfr, a JFR recording of the same minute goes to tools/perf/results/<tag>-<level>.jfr.
"""
import csv
import pathlib
import re
import subprocess
import sys
import time

from rcon_client import Rcon

REPO = pathlib.Path(__file__).resolve().parents[2]
CONFIG = REPO / "run-server" / "config" / "zoocraft-server.toml"
PERF_CSV = REPO / "run-server" / "logs" / "zoocraft-perf.csv"
RESULTS = pathlib.Path(__file__).resolve().parent / "results"


def set_limit(n):
    text = CONFIG.read_text()
    CONFIG.write_text(re.sub(r"maxVisitorsPerZoo = \d+", f"maxVisitorsPerZoo = {n}", text))


def visitor_count(r):
    m = re.search(r"count: (\d+)", r.command("execute if entity @e[type=zoocraft:visitor]"))
    return int(m.group(1)) if m else 0


def tick_query(r):
    out = r.command("tick query")
    avg = float(re.search(r"Average time per tick: ([\d.]+)ms", out).group(1))
    p = re.search(r"P50: ([\d.]+)ms P95: ([\d.]+)ms P99: ([\d.]+)ms", out)
    return avg, float(p.group(2)), float(p.group(3))


def perf_averages():
    with PERF_CSV.open() as f:
        rows = list(csv.DictReader(f))
    if not rows:
        return {}
    return {k: sum(float(row[k]) for row in rows) / len(rows) for k in rows[0] if k != "seconds"}


def main():
    tag, zoos, levels = sys.argv[1], sys.argv[2].split(","), [int(x) for x in sys.argv[3].split(",")]
    pid = sys.argv[sys.argv.index("--jfr") + 1] if "--jfr" in sys.argv else None
    RESULTS.mkdir(exist_ok=True)
    out = RESULTS / f"{tag}.md"
    if not out.exists():
        out.write_text("| Limit per zoo | Visitors | Avg tick ms | Worst P95 | Worst P99 | ZooCraft ms | Path following ms |\n"
                       "|---|---|---|---|---|---|---|\n")
    r = Rcon()
    for n in levels:
        set_limit(n)
        target = int(n * len(zoos) * 0.9)
        start = time.time()
        # Spawn groups directly rather than waiting for the spawner; the zoo's limit still caps them.
        while time.time() - start < 420 and visitor_count(r) < target:
            for zoo in zoos:
                for _ in range(10):
                    r.command(f'zoo debug spawn "{zoo}"')
            time.sleep(1)
        time.sleep(30)
        if pid:
            subprocess.run(["jcmd", pid, "JFR.start", f"name={tag}{n}", "settings=profile", "duration=60s",
                            f"filename={RESULTS / f'{tag}-{n}.jfr'}"], capture_output=True)
        r.command("zoo debug perf log 60")
        samples, counts = [], []
        for _ in range(12):
            time.sleep(5)
            samples.append(tick_query(r))
            counts.append(visitor_count(r))
        time.sleep(3)
        perf = perf_averages()
        line = (f"| {n} | {sum(counts) / len(counts):.0f} | {sum(s[0] for s in samples) / len(samples):.1f} "
                f"| {max(s[1] for s in samples):.1f} | {max(s[2] for s in samples):.1f} "
                f"| {perf.get('zoocraft_total_ms', 0):.2f} | {perf.get('visitor_navigation_ms', 0):.2f} |")
        print(line, flush=True)
        with out.open("a") as f:
            f.write(line + "\n")


if __name__ == "__main__":
    main()
