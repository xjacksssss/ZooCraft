# ZooCraft

A zoo-building and management mod for Minecraft 1.21.1 (NeoForge). Mark out a zoo anywhere in your world, build
enclosures for any animals you like, open the gates, and watch villager visitors arrive, pay admission, walk your paths
to see the animals, buy food from your shops and tell you what they think.

ZooCraft works on its own and in the All the Mods 10 (ATM10) modpack, in single player and on servers, where a zoo's
owner can share the running of it with other players.

## Features

- **Zoos of any shape.** Place boundary corners with the Zoo Boundary Tool. A zoo can have several gates, and its
  boundary can be edited later without touching anything built inside it.
- **Real walking visitors.** Villagers arrive alone or in groups, queue at the gates, pay admission from their own
  budget, explore, and leave when they've had enough. Budget-conscious visitors, animal enthusiasts and general
  visitors each behave differently. They follow your paths, climb ramps, cross bridges and never get trapped, even when
  you close the zoo or block a gate.
- **Any animal, from any mod.** Animals are recognised automatically, vanilla and modded alike, without registering
  anything. Villagers, golems, bosses, familiars and summoned creatures aren't counted. Modpacks can adjust this with
  entity tags.
- **Exhibits that are rated, not policed.** Each enclosure gets viewing spots and a plain-language rating for access,
  space and surroundings. Poor conditions give warnings and lower ratings; animals never die because of them. Animals
  inside a zoo never despawn.
- **A working business.** Designate shops with the Zoo Designation Tool and stock them from ordinary chests, barrels and
  modded storage. Visitors buy food, drinks and souvenirs with emeralds, need toilets, and drop litter if you don't
  provide rubbish bins. Every sale and admission is recorded exactly once.
- **A dashboard** (default key `;`) with attendance, finances, satisfaction, reputation, daily reports and advice that
  names the problem ("visitors can't get close enough to see the animals").
- **Shared management.** The owner can add managers who share the zoo's treasury and see the same dashboard. Zoos keep
  running while the owner is offline.

## Installing

1. Install [NeoForge](https://neoforged.net/) **21.1.251** or later for Minecraft 1.21.1.
2. Download [SmartBrainLib](https://www.curseforge.com/minecraft/mc-mods/smartbrainlib) 1.16.11 or later for
   NeoForge 1.21.1. ZooCraft needs it; ATM10 already includes it.
3. Download `zoocraft-<version>.jar` from the [Releases](../../releases) page and put both jars in your `mods` folder.

Install ZooCraft on both the server and every client.

## Getting started

1. Craft a **Zoo Boundary Tool** (stick, emerald and string, shapeless). Use it on the ground to place the corners of
   your zoo, then use the first corner again to finish and name the zoo.
2. Craft a **Zoo Designation Tool** (stick, emerald and paper). In Gate mode, use it on the ground at the boundary to
   add at least one gate. Sneak and scroll to change mode: Shop mode places a shop counter and links nearby containers
   as its stock; Toilet mode marks where visitors use a toilet.
3. Build fenced enclosures, bring animals in, and lay paths (gravel, planks, stone bricks and many other blocks count
   as paths).
4. Open the zoo from the dashboard or with `/zoo open "<zoo name>"`. Visitors start arriving at your gates.
5. Craft **Rubbish Bins** (iron and green dye) and place them along your paths.

`/zoo` opens the dashboard. Other commands take the zoo's name last, in quotes if it has spaces; leave it out to mean the
zoo you are standing in.

| Command | What it does |
|---|---|
| `/zoo list` | Your zoos |
| `/zoo info [zoo]` | Treasury, admission, attendance, satisfaction, gates |
| `/zoo open [zoo]`, `/zoo close [zoo]` | Open or close the gates |
| `/zoo price <emeralds> [zoo]` | Set the admission price |
| `/zoo deposit <emeralds> [zoo]`, `/zoo withdraw <emeralds> [zoo]` | Move emeralds between you and the treasury |
| `/zoo manager add <player> [zoo]`, `/zoo manager remove <player> [zoo]` | Share management (owner only) |
| `/zoo shop list [zoo]` | Shops, their stock and takings |
| `/zoo keeploaded true [zoo]` | Keep the zoo running with nobody nearby, if the server allows it |

## Configuration

Server settings are in `config/zoocraft-server.toml` (per world on servers: `serverconfig/`). The ones most worth
knowing:

| Setting | Default | |
|---|---|---|
| `visitors.maxVisitorsTotal` | 140 | Visitors across all zoos. Lower this first on a weaker server. |
| `visitors.maxVisitorsPerZoo` | 68 | Visitors in one zoo at a time. |
| `chunkLoading.allowZooChunkLoading` | true | Lets owners keep a zoo loaded; `maxLoadedChunksPerZoo` caps it. |
| `animals.viewingRadius` | 6 | How close a visitor must be to see an animal. |

The defaults were measured on a mid-range desktop (Ryzen 5 5600X), where 200 visitors add about 5 ms to the server
tick. See [docs/performance.md](docs/performance.md) for what to expect and how to tune a weaker server.

## Compatibility

- Tested in ATM10 8.2 with all of its mods. A recognition audit spawned every one of its 787 mob types: 217 count as
  zoo animals, including Twilight Forest, the Aether, Aquaculture, the Undergarden, Productive Bees, Living Things and
  Eternal Starlight creatures.
- Shop stock works with any container that offers NeoForge's item handler (chests, barrels, Sophisticated Storage,
  Functional Storage drawers and so on).
- Known limits are listed in [docs/compatibility.md](docs/compatibility.md).

## Building from source

Requires JDK 21.

```bash
./gradlew build                 # compiles, runs the unit tests, writes build/libs/zoocraft-<version>.jar
./gradlew runGameTestServer     # runs the in-world GameTests
./gradlew runClient             # starts a development client
```

Pushing a tag such as `v1.0.0` builds the jar on GitHub Actions and attaches it to a release for that tag; the tag
must match `mod_version` in `gradle.properties`.

## Licence

All rights reserved. © Jack Sapsford.
