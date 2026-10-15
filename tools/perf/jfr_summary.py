"""Inclusive share of server-thread CPU samples per bucket and per method in a JFR recording (Phase 3 spec 3.2).

Usage: python tools/perf/jfr_summary.py <file.jfr> [top_methods]
Needs the JDK's jfr tool on PATH.
"""
import collections
import json
import subprocess
import sys

BUCKETS = [
    ("visitor brain and controller", "com.zoocraft.visitor.Visitor.customServerAiStep"),
    ("SmartBrainLib navigation (visitors)", "net.tslat.smartbrainlib.api.core.navigation."),
    ("  path smoothing (attemptShortcut)", "net.tslat.smartbrainlib.api.core.navigation.ExtendedNavigator.attemptShortcut"),
    ("pathfinding, all mobs", "net.minecraft.world.level.pathfinder.PathFinder.findPath"),
    ("entity pushing", "net.minecraft.world.entity.LivingEntity.pushEntities"),
    ("entity move and collide", "net.minecraft.world.entity.Entity.move"),
    ("all living entity ticks", "net.minecraft.world.entity.LivingEntity.tick"),
    ("animal census", "com.zoocraft.animal.AnimalCensus.refresh"),
    ("facility census", "com.zoocraft.shop.FacilityCensus.refresh"),
    ("entity tracking (ChunkMap.tick)", "net.minecraft.server.level.ChunkMap.tick"),
    ("any ZooCraft frame", "com.zoocraft."),
]


def frames(event):
    for f in event["values"]["stackTrace"]["frames"]:
        m = f["method"]
        yield m["type"]["name"].replace("/", ".") + "." + m["name"]


def main():
    path = sys.argv[1]
    top = int(sys.argv[2]) if len(sys.argv) > 2 else 25
    raw = subprocess.run(["jfr", "print", "--json", "--stack-depth", "256", "--events", "jdk.ExecutionSample", path],
                         capture_output=True, text=True, encoding="utf-8").stdout
    events = json.loads(raw)["recording"]["events"]
    server = [e for e in events if (e["values"].get("sampledThread") or {}).get("javaName") == "Server thread"]
    n = max(len(server), 1)
    inclusive, buckets = collections.Counter(), collections.Counter()
    for e in server:
        names = list(frames(e))
        for name in set(names):
            inclusive[name] += 1
        for label, prefix in BUCKETS:
            if any(x.startswith(prefix) for x in names):
                buckets[label] += 1
    print(f"{path}: {len(server)} server-thread samples")
    for label, _ in BUCKETS:
        print(f"  {label:38s} {100.0 * buckets[label] / n:5.1f}%")
    print("  top ZooCraft, pathfinder and SmartBrainLib methods (inclusive):")
    shown = 0
    for name, c in inclusive.most_common():
        if name.startswith(("com.zoocraft", "net.minecraft.world.level.pathfinder", "net.tslat")):
            print(f"    {100.0 * c / n:5.1f}%  {name}")
            shown += 1
            if shown >= top:
                break


if __name__ == "__main__":
    main()
