"""Builds "Hill Park": a 160 by 160 park with height changes, winding paths and well-spaced pens of different sizes.

Usage: python tools/perf/build_complex_park.py ["<name>"] [x0] [z0]      (defaults: "Hill Park" 800 0)
Like build_park.py it needs the superflat perf world (feet at y=-60), Dev online and opped, and the area force-loaded
(`forceload add 768 -32 991 191`: the park plus a two-chunk margin, so visitors outside the gates keep ticking).

The layout (relative coordinates, x east and z south, 0 to 159):
- three raised terraces (+3, +6 on top of it, and +4) and a sunken pond with fish, fenced at ground level;
- one winding loop path, 3 wide, with spurs to a west and an east gate and the main gate in the south; where it climbs
  a terrace it ramps up one block every two, built out as an embankment;
- a raised boardwalk (+3, planks, fence railings) across the pond with ramps at both ends;
- pens of 7 to 15 blocks placed along the path on alternate sides, each on one level, at least 3 blocks from the path
  and 3 from each other; 2 or 3 animals of one species each, the bigger animals in the bigger pens;
- shops (stocked barrels), toilets and bins along the path.
Prints a summary of what it built and any command that failed.
"""
import math
import random
import sys

from rcon_client import Rcon

FLOOR = -61  # top block of the superflat ground; feet stand at FLOOR + 1
SIZE = 160
# (x1, z1, x2, z2, level): later entries win where they overlap.
TERRACES = [(90, 0, 159, 69, 3), (115, 0, 159, 40, 6), (0, 0, 50, 45, 4)]
POND = (20, 105, 45, 135)
BRIDGE_LEVEL = 3
LOOP = [(80, 140), (62, 124), (58, 100), (70, 82), (60, 60), (40, 58), (25, 50), (20, 30), (30, 12), (55, 15), (75, 30),
        (95, 42), (110, 25), (125, 15), (145, 28), (140, 55), (120, 62), (105, 80), (125, 100), (145, 115), (140, 140),
        (115, 150), (95, 146), (80, 140)]
SPURS = [
    [(80, 140), (80, 159)],                                        # south gate
    [(58, 100), (32, 94), (0, 92)],                                # west gate
    [(125, 100), (150, 94), (159, 92)],                            # east gate
    [(62, 124), (52, 120), (14, 120), (10, 134), (25, 150), (60, 152), (80, 150)],  # boardwalk over the pond and back
]
GATES = [(80, 159), (0, 92), (159, 92)]
SMALL = ["chicken", "rabbit", "fox", "armadillo", "sheep", "pig", "turtle", "frog"]
LARGE = ["cow", "horse", "donkey", "llama", "panda", "polar_bear", "mooshroom", "camel", "goat", "sniffer"]
FISH = ["cod", "cod", "salmon", "salmon", "tropical_fish", "axolotl"]


def terrain(x, z):
    level = 0
    for x1, z1, x2, z2, h in TERRACES:
        if x1 <= x <= x2 and z1 <= z <= z2:
            level = h
    return level


def in_pond(x, z, margin=0):
    return POND[0] - margin <= x <= POND[2] + margin and POND[1] - margin <= z <= POND[3] + margin


def chaikin(points, rounds=2):
    for _ in range(rounds):
        out = [points[0]]
        for (ax, az), (bx, bz) in zip(points, points[1:]):
            out.append((0.75 * ax + 0.25 * bx, 0.75 * az + 0.25 * bz))
            out.append((0.25 * ax + 0.75 * bx, 0.25 * az + 0.75 * bz))
        out.append(points[-1])
        points = out
    return points


def samples(points):
    """One point per block along the smoothed line, with its unit direction."""
    out = []
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        length = math.hypot(bx - ax, bz - az)
        steps = max(1, int(length))
        for i in range(steps):
            t = i / steps
            out.append((ax + (bx - ax) * t, az + (bz - az) * t, (bx - ax) / length, (bz - az) / length))
    return out


def path_heights(line, bridge):
    """Each sample's height: the terrain, or the bridge level over the pond, raised so it changes by at most 1 in 2."""
    want = []
    for x, z, _, _ in line:
        near_pond = bridge and in_pond(int(x), int(z), margin=2)
        want.append(BRIDGE_LEVEL if near_pond else terrain(int(x), int(z)))
    h = list(map(float, want))
    for _ in range(2):
        for i in range(1, len(h)):
            h[i] = max(h[i], h[i - 1] - 0.5)
        for i in range(len(h) - 2, -1, -1):
            h[i] = max(h[i], h[i + 1] - 0.5)
    return [int(v) for v in h]


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "Hill Park"
    x0 = int(sys.argv[2]) if len(sys.argv) > 2 else 800
    z0 = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    rng = random.Random(20261016)
    r = Rcon()
    replies = []

    def run(cmd):
        replies.append((cmd, r.command(cmd)))

    # Terrain: terraces and the pond.
    for x1, z1, x2, z2, h in TERRACES:
        run(f"fill {x0 + x1} {FLOOR + 1} {z0 + z1} {x0 + x2} {FLOOR + h - 1} {z0 + z2} dirt")
        run(f"fill {x0 + x1} {FLOOR + h} {z0 + z1} {x0 + x2} {FLOOR + h} {z0 + z2} grass_block")
    px1, pz1, px2, pz2 = POND
    run(f"fill {x0 + px1} {FLOOR - 2} {z0 + pz1} {x0 + px2} {FLOOR} {z0 + pz2} water")
    for x1, z1, x2, z2 in [(px1 - 1, pz1 - 1, px2 + 1, pz1 - 1), (px1 - 1, pz2 + 1, px2 + 1, pz2 + 1),
                           (px1 - 1, pz1 - 1, px1 - 1, pz2 + 1), (px2 + 1, pz1 - 1, px2 + 1, pz2 + 1)]:
        run(f"fill {x0 + x1} {FLOOR + 1} {z0 + z1} {x0 + x2} {FLOOR + 1} {z0 + z2} oak_fence")

    # Paths: cell -> (height, is_bridge). Later lines don't lower earlier cells.
    cells = {}
    lines = [(LOOP, False)] + [(spur, i == 3) for i, spur in enumerate(SPURS)]
    all_samples = []
    for points, bridge in lines:
        line = samples(chaikin(points))
        heights = path_heights(line, bridge)
        for (x, z, dx, dz), h in zip(line, heights):
            all_samples.append((x, z, dx, dz, h))
            for cx in range(int(x) - 2, int(x) + 3):
                for cz in range(int(z) - 2, int(z) + 3):
                    if 0 <= cx < SIZE and 0 <= cz < SIZE and math.hypot(cx + 0.5 - x, cz + 0.5 - z) <= 1.6:
                        on_bridge = bridge and h > terrain(cx, cz) + 1 or in_pond(cx, cz, 1)
                        old = cells.get((cx, cz))
                        if old is None or h > old[0]:
                            cells[(cx, cz)] = (h, on_bridge)
    for (cx, cz), (h, on_bridge) in cells.items():
        t = terrain(cx, cz)
        x, z = x0 + cx, z0 + cz
        if on_bridge:
            run(f"setblock {x} {FLOOR + h} {z} oak_planks")
            if (cx + cz) % 4 == 0 and not in_pond(cx, cz):
                run(f"fill {x} {FLOOR + 1} {z} {x} {FLOOR + h - 1} {z} oak_log")
            continue
        if h > t:
            run(f"fill {x} {FLOOR + t + 1} {z} {x} {FLOOR + h - 1} {z} dirt")
        run(f"setblock {x} {FLOOR + h} {z} gravel")
    # Railings along the boardwalk and on embankments more than one block above the ground beside them.
    rails = 0
    for (cx, cz), (h, on_bridge) in cells.items():
        for nx, nz in [(cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)]:
            if (nx, nz) in cells or not (0 <= nx < SIZE and 0 <= nz < SIZE):
                continue
            if on_bridge or h - terrain(nx, nz) >= 2:
                if on_bridge:
                    run(f"setblock {x0 + nx} {FLOOR + h} {z0 + nz} oak_planks")
                else:
                    run(f"fill {x0 + nx} {FLOOR + terrain(nx, nz) + 1} {z0 + nz} {x0 + nx} {FLOOR + h} {z0 + nz} dirt")
                run(f"setblock {x0 + nx} {FLOOR + h + 1} {z0 + nz} oak_fence")
                rails += 1

    # Pens along the path, alternate sides, each on one level and clear of paths, the pond and each other.
    pens = []
    blocked = set()  # pens and the 3 blocks round them

    def free(x1, z1, x2, z2, level):
        if x1 < 3 or z1 < 3 or x2 > SIZE - 4 or z2 > SIZE - 4:
            return False
        for x in range(x1 - 2, x2 + 3):
            for z in range(z1 - 2, z2 + 3):
                if (x, z) in cells or in_pond(x, z, 2):
                    return False
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if (x, z) in blocked:
                    return False
        return all(terrain(x, z) == level for x in range(x1 - 1, x2 + 2) for z in range(z1 - 1, z2 + 2))

    side = 1
    for i in range(0, len(all_samples), 5):
        x, z, dx, dz, _ = all_samples[i]
        size = rng.choice([7, 9, 9, 11, 11, 13, 15])
        for flip in (side, -side):
            gap = 1.6 + 3.5 + size / 2
            cx, cz = x - dz * flip * gap, z + dx * flip * gap
            x1, z1 = int(cx - size / 2), int(cz - size / 2)
            x2, z2 = x1 + size - 1, z1 + size - 1
            level = terrain(x1, z1)
            if free(x1, z1, x2, z2, level):
                pens.append((x1, z1, x2, z2, level))
                for bx in range(x1 - 3, x2 + 4):
                    for bz in range(z1 - 3, z2 + 4):
                        blocked.add((bx, bz))
                side = -flip
                break
    animals = 0
    species_used = set()
    for n, (x1, z1, x2, z2, level) in enumerate(pens):
        y = FLOOR + level + 1
        ax1, az1, ax2, az2 = x0 + x1, z0 + z1, x0 + x2, z0 + z2
        for a, b, c, d in [(ax1, az1, ax2, az1), (ax1, az2, ax2, az2), (ax1, az1, ax1, az2), (ax2, az1, ax2, az2)]:
            run(f"fill {a} {y} {b} {c} {y} {d} oak_fence")
        pool = LARGE if x2 - x1 >= 10 else SMALL
        species = pool[n % len(pool)]
        species_used.add(species)
        for k in range(2 + n % 2):
            run(f"summon {species} {ax1 + 2 + 2 * k + 0.5} {y} {az1 + (z2 - z1) / 2 + 0.5} {{PersistenceRequired:1b}}")
            animals += 1
    for k, fish in enumerate(FISH):
        run(f"summon {fish} {x0 + px1 + 4 + 3 * k + 0.5} {FLOOR - 1} {z0 + pz1 + 10.5} {{PersistenceRequired:1b}}")
    species_used.update(FISH)

    run(f'execute as Dev run zoo debug create "{name}" {x0} {FLOOR + 1} {z0} {x0 + SIZE - 1} {FLOOR + 1} {z0 + SIZE - 1}')
    for gx, gz in GATES:
        run(f"execute as Dev run zoo debug gate {x0 + gx} {FLOOR + cells.get((gx, gz), (0, False))[0] + 1} {z0 + gz}")

    # Facilities beside the loop, one every 30 blocks: shops, toilets and bins, 3 blocks to one side of the path.
    shops = toilets = bins = 0
    loop_samples = all_samples[:len(samples(chaikin(LOOP)))]
    for k, i in enumerate(range(30, len(loop_samples), 30)):
        x, z, dx, dz, h = loop_samples[i]
        sx, sz = int(x - dz * 3), int(z + dx * 3)
        in_pen = any(p[0] - 1 <= sx <= p[2] + 1 and p[1] - 1 <= sz <= p[3] + 1 for p in pens)
        if (sx, sz) in cells or in_pen or in_pond(sx, sz, 1) or terrain(sx, sz) != h:
            continue
        feet = FLOOR + h + 1
        if k % 4 == 1:
            run(f'setblock {x0 + sx} {feet} {z0 + sz} barrel{{Items:[{{Slot:0b,id:"minecraft:bread",count:64}},'
                f'{{Slot:1b,id:"minecraft:cookie",count:64}},{{Slot:2b,id:"minecraft:honey_bottle",count:16}},'
                f'{{Slot:3b,id:"minecraft:poppy",count:64}}]}}')
            run(f"execute as Dev run zoo debug shop {x0 + int(x)} {feet} {z0 + int(z)}")
            run(f"execute as Dev run zoo debug link {x0 + sx} {feet} {z0 + sz}")
            shops += 1
        elif k % 4 == 3:
            run(f"execute as Dev run zoo debug toilet {x0 + sx} {feet} {z0 + sz}")
            toilets += 1
        else:
            run(f"setblock {x0 + sx} {feet} {z0 + sz} zoocraft:rubbish_bin")
            bins += 1

    run(f'execute as Dev run zoo open "{name}"')
    problems = [(c, o) for c, o in replies if "Unknown" in o or "Incorrect" in o or "rror" in o or "ouldn't" in o
                or "not " in o.lower() and "zoo debug" in c]
    sizes = sorted(p[2] - p[0] + 1 for p in pens)
    print(f"{len(replies)} commands sent for {name}: {len(cells)} path blocks, {rails} railing posts, {len(pens)} pens "
          f"(sizes {sizes[0]} to {sizes[-1]}, levels {sorted({p[4] for p in pens})}), {animals} animals plus "
          f"{len(FISH)} in the pond, {len(species_used)} species, {shops} shops, {toilets} toilets, {bins} bins")
    for c, o in replies:
        if " create " in c or " gate " in c or " open " in c:
            print(">", c, "\n ", o)
    for c, o in problems[:20]:
        print("PROBLEM:", c, "\n ", o)


if __name__ == "__main__":
    main()
