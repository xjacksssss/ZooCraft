"""Compares a recognition audit with the reviewed expectations (Phase 3 spec section 9.1).

Usage: python tools/atm10/check_recognition.py [audit.csv] [--accept]
The audit CSV defaults to run-atm10/logs/zoocraft-recognition-audit.csv (written by /zoo debug recognise audit).
The expectations are tools/atm10/recognition-expected.csv: entity, recognised (true/false) and a note. A note starting
"known problem" lets that entity's audit problem through.

Fails (exit 1) when an entity's verdict differs from its expectation, when an audit row reports a problem that isn't
known, or when an entity has no expectation yet (a new mod or a new ATM10 version). --accept writes the expectations
from this audit instead, keeping existing notes: run it only after reviewing every difference it would accept.
"""
import csv
import pathlib
import sys
from collections import Counter

REPO = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_AUDIT = REPO / "run-atm10" / "logs" / "zoocraft-recognition-audit.csv"
EXPECTED = pathlib.Path(__file__).resolve().parent / "recognition-expected.csv"


def read(path):
    with path.open(encoding="utf-8", newline="") as f:
        return {row["entity"]: row for row in csv.DictReader(f)}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    audit_path = pathlib.Path(args[0]) if args else DEFAULT_AUDIT
    audit = read(audit_path)
    expected = read(EXPECTED) if EXPECTED.exists() else {}

    if "--accept" in sys.argv:
        with EXPECTED.open("w", encoding="utf-8", newline="") as f:
            out = csv.writer(f, lineterminator="\n")
            out.writerow(["entity", "recognised", "note"])
            for entity in sorted(audit):
                note = expected.get(entity, {}).get("note", "")
                out.writerow([entity, audit[entity]["recognised"], note])
        print(f"Wrote {len(audit)} expectations to {EXPECTED}")
        return 0

    wrong, problems, new = [], [], []
    for entity, row in sorted(audit.items()):
        want = expected.get(entity)
        if want is None:
            new.append(f"{entity}: {row['recognised']} ({row['reason']})")
            continue
        if want["recognised"] != row["recognised"]:
            wrong.append(f"{entity}: expected {want['recognised']}, got {row['recognised']} ({row['reason']})"
                         + (f" - note: {want['note']}" if want["note"] else ""))
        if row["problem"] and not want["note"].startswith("known problem"):
            problems.append(f"{entity}: {row['problem']}")
    gone = sorted(set(expected) - set(audit))

    recognised = Counter(r["mod"] for r in audit.values() if r["recognised"] == "true")
    print(f"{len(audit)} entity types audited, {sum(recognised.values())} recognised, "
          f"{sum(1 for r in audit.values() if r['spawned'] == 'true')} spawned.")
    for title, items in [("Verdicts that differ from the expectations", wrong), ("Problems", problems),
                         ("Entities with no expectation", new), ("Expected entities no longer present", gone)]:
        if items:
            print(f"\n{title} ({len(items)}):")
            for item in items:
                print("  " + item)
    if wrong or problems or new:
        print("\nFAILED")
        return 1
    print("\nPASSED: every entity matches its reviewed expectation.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
