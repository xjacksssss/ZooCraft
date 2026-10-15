"""Switches the dev server to a separate perf world with raised limits, and back.

Usage: python tools/perf/perf_world.py prepare|restore
prepare backs up run-server/server.properties and run-server/config/zoocraft-server.toml into run-server/perf-backup/,
sets level-name=perf-world, and sets maxVisitorsPerZoo=0, maxVisitorsTotal=1000, spawnIntervalSeconds=1.
restore puts the backups back.
"""
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2] / "run-server"
FILES = [ROOT / "server.properties", ROOT / "config" / "zoocraft-server.toml"]
BACKUP = ROOT / "perf-backup"


def prepare():
    if BACKUP.exists():
        sys.exit("run-server/perf-backup already exists: run 'restore' first")
    BACKUP.mkdir()
    for f in FILES:
        shutil.copy2(f, BACKUP / f.name)
    props = FILES[0].read_text()
    FILES[0].write_text(re.sub(r"(?m)^level-name=.*$", "level-name=perf-world", props))
    cfg = FILES[1].read_text()
    for key, value in [("maxVisitorsPerZoo", 0), ("maxVisitorsTotal", 1000), ("spawnIntervalSeconds", 1)]:
        cfg = re.sub(rf"{key} = \d+", f"{key} = {value}", cfg)
    FILES[1].write_text(cfg)
    print("Perf world prepared. Start runServer, then runClient -PjoinServer.")


def restore():
    if not BACKUP.exists():
        sys.exit("Nothing to restore")
    for f in FILES:
        shutil.copy2(BACKUP / f.name, f)
    shutil.rmtree(BACKUP)
    print("Dev server settings restored.")


if __name__ == "__main__":
    {"prepare": prepare, "restore": restore}[sys.argv[1]]()
