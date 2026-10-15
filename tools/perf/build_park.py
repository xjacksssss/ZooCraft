"""Builds a test park on the dev server (superflat world, feet at y=-60), Phase 3 spec section 3.1.

Usage: python tools/perf/build_park.py "<name>" <x0> <z0> <cells> [animals_per_pen]
A park is cells x cells cells of 16 blocks: 2-wide gravel paths on the cell edges, an 8x8 fenced pen in each cell,
a gate in the middle of each side, a shop every 4th cell, a toilet every 5th and a bin every 3rd.
The reference "Big Park" is: "Big Park" 0 0 15 2. The small parks are 6 cells.
Needs a player called Dev online and opped (it runs the zoo commands as Dev), and the area force-loaded.
"""
import sys

from rcon_client import Rcon

Y, FLOOR, CELL = -60, -61, 16
SPECIES = ["cow", "sheep", "pig", "chicken", "horse", "donkey", "llama", "rabbit", "fox", "panda",
           "polar_bear", "mooshroom", "camel", "armadillo", "turtle"]


def main():
    name, x0, z0, cells = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    per_pen = int(sys.argv[5]) if len(sys.argv) > 5 else 2
    size = cells * CELL
    x1, z1 = x0 + size - 1, z0 + size - 1
    r = Rcon()
    replies = []

    def run(cmd):
        replies.append((cmd, r.command(cmd)))

    for i in range(cells + 1):
        a = min(x0 + i * CELL, x1 - 1)
        run(f"fill {a} {FLOOR} {z0} {a + 1} {FLOOR} {z1} gravel")
        b = min(z0 + i * CELL, z1 - 1)
        run(f"fill {x0} {FLOOR} {b} {x1} {FLOOR} {b + 1} gravel")

    for cx in range(cells):
        for cz in range(cells):
            bx, bz = x0 + cx * CELL, z0 + cz * CELL
            fx0, fz0, fx1, fz1 = bx + 4, bz + 4, bx + 11, bz + 11
            run(f"fill {fx0} {Y} {fz0} {fx1} {Y} {fz0} oak_fence")
            run(f"fill {fx0} {Y} {fz1} {fx1} {Y} {fz1} oak_fence")
            run(f"fill {fx0} {Y} {fz0} {fx0} {Y} {fz1} oak_fence")
            run(f"fill {fx1} {Y} {fz0} {fx1} {Y} {fz1} oak_fence")
            species = SPECIES[(cx * cells + cz) % len(SPECIES)]
            for k in range(per_pen):
                run(f"summon {species} {bx + 6 + 2 * k + 0.5} {Y} {bz + 7.5} {{PersistenceRequired:1b}}")

    run(f'execute as Dev run zoo debug create "{name}" {x0} {Y} {z0} {x1} {Y} {z1}')
    mid_x, mid_z = x0 + (cells // 2) * CELL + 8, z0 + (cells // 2) * CELL + 8
    for gx, gz in [(mid_x, z0), (mid_x, z1), (x0, mid_z), (x1, mid_z)]:
        run(f"execute as Dev run zoo debug gate {gx} {Y} {gz}")

    index = 0
    for cx in range(1, cells):
        for cz in range(cells):
            index += 1
            px, pz = x0 + cx * CELL, z0 + cz * CELL + 8
            if index % 4 == 0:
                run(f'setblock {px + 2} {Y} {pz} barrel{{Items:[{{Slot:0b,id:"minecraft:bread",count:64}},'
                    f'{{Slot:1b,id:"minecraft:bread",count:64}},{{Slot:2b,id:"minecraft:cookie",count:64}},'
                    f'{{Slot:3b,id:"minecraft:honey_bottle",count:16}},{{Slot:4b,id:"minecraft:poppy",count:64}}]}}')
                run(f"execute as Dev run zoo debug shop {px} {Y} {pz}")
                run(f"execute as Dev run zoo debug link {px + 2} {Y} {pz}")
            elif index % 5 == 0:
                run(f"execute as Dev run zoo debug toilet {px} {Y} {pz + 3}")
            if index % 3 == 0:
                run(f"setblock {px + 2} {Y} {pz + 4} zoocraft:rubbish_bin")

    run(f'execute as Dev run zoo open "{name}"')
    problems = [(c, o) for c, o in replies if "Unknown" in o or "Incorrect" in o or "rror" in o]
    print(f"{len(replies)} commands sent for {name}; {cells * cells} pens, {cells * cells * per_pen} animals")
    for c, o in replies:
        if " create " in c or " gate " in c or " open " in c:
            print(">", c, "\n ", o)
    for c, o in problems[:20]:
        print("PROBLEM:", c, "\n ", o)


if __name__ == "__main__":
    main()
