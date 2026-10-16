# ZooCraft compatibility notes

Known gaps are recorded here rather than hidden (GDD section 5.4).

## Test environments

- **Standalone:** NeoForge 21.1.251 dedicated server installed from the official installer, with only `zoocraft-0.1.0.jar` and `SmartBrainLib-neoforge-1.21.1-1.16.11.jar` in `mods`. JTS loads from inside the ZooCraft jar (jar-in-jar).
- **ATM10:** ATM10 8.2 (NeoForge 21.1.251, 491 mods). Mods and configs are copied into `run-atm10` by `tools/sync-atm10-mods.ps1`, and the game is started with `./gradlew runAtm10Client -Patm10` (add `-PquickPlay=<save>` to open a world from `run-atm10/saves` directly). The `-Patm10` flag makes ModDevGradle use the original Minecraft jar instead of a recompiled one. Without it, mods whose mixins target synthetic lambda names (for example Accessories' `EntityRenderersMixin`) fail to apply, which is a dev-environment artefact rather than a real conflict.

## Results

| Date | Milestone | Environment | Result |
|---|---|---|---|
| 2026-10-14 | M0 | ATM10 | ZooCraft 0.1.0 loads with all ATM10 mods and reaches the title screen. SmartBrainLib 1.16.11 is shared with ATM10's copy; no duplicate-mod error. |
| 2026-10-14 | M4 | Standalone server | Loads; a saved zoo world reloads intact; visitors keep arriving and paying with no players online (zoo inside spawn chunks); no errors. |
| 2026-10-14 | M4 | Dev server + 2 clients | Owner and manager see the same dashboard numbers live; manager permissions match the GDD table; closing from the dashboard sends every visitor out; zoo data survives a server restart. |
| 2026-10-14 | M4 | ATM10 | A zoo world opens with all ATM10 mods; visitors arrive, pay, walk between pens and comment; outlines render; the dashboard opens on the default `;` key and shows live data; JourneyMap shows visitors as villagers. |
| 2026-10-15 | M5–M8 | Dev server + 2 clients | Owner and manager see the same Shops tab; a price change by one shows on the other within a second; a restock shows straight away; removing the manager closes their dashboard and refuses their shop commands. A server restart with visitors mid-purchase keeps the treasury, shop takings, toilets, history and litter exactly as before. |
| 2026-10-16 | M12 | ATM10 | Recognition audit (below): 1,417 entity types, 787 mobs spawned in a zoo and counted by the census, 217 recognised, every verdict matching the reviewed expectations. The `zootest` save, last written by the Phase 2 build, loads with its zoo, gate, treasury and attendance intact. |
| 2026-10-16 | M12 | Standalone server | NeoForge 21.1.251 from the official installer, only `zoocraft-0.1.0.jar` (with JTS inside) and SmartBrainLib 1.16.11 in `mods`, joined by the dev client. A zoo with two pens, a shop and a toilet: 79 visitors paid 158 emeralds admission and spent 48 at the shop, treasury 206; `/zoo debug perf` showed 26 visitors at 0.28 ms per tick and none stuck. After `stop` and a restart: treasury 206, admission 158, 79 visitors, shop takings 48, visitors still inside. No errors in the log. |
| 2026-10-15 | M5–M8 | ATM10 | ZooCraft loads with all ATM10 mods and upgrades a zoo saved by Phase 1. A vanilla chest, a Sophisticated Storage chest and a Functional Storage drawer all link as shop stock; a cookie hoppered into the Sophisticated Storage chest sells through the item handler. Farmer's Delight items are classed automatically: Hamburger and Tomato as food, Apple Cider as a drink (through `c:drinks`). |

## Recognition defaults

- Recognised automatically: anything extending vanilla `Animal` or `WaterAnimal`, or registered in the creature, water creature, water ambient, underground water creature or axolotl spawn categories. Modpacks can add more with the `zoocraft:exhibit_animals` entity type tag.
- Never recognised: `c:bosses` (Cataclysm, Twilight Forest and the Aether tag their bosses this way), villagers and traders (anything implementing vanilla's `Npc` or `Merchant`, whatever its spawn category), the monster spawn category unless tagged in, and `zoocraft:not_exhibit_animals`, which ships with villagers, wandering traders, iron and snow golems, ZooCraft visitors, and optional entries for the ATM10 creatures listed below.
- Separate entity types that are really one animal are merged with the `zoocraft:species` data map. ZooCraft ships merges, each applied only when its mods are loaded, for Shiny's variants (into the vanilla species), Dyenamics' sheep and Sushi Go Crafting's tuna (into Aquaculture's).
- Exhibit animals inside a zoo never despawn and take no entity-cramming damage. The census marks every animal it counts as persistent (saved with the animal, like a name tag), and a despawn check inside a zoo marks the animal too, so animals unload with their chunk and come back when a player is near again. The mark stays if the animal later leaves the zoo.

## ATM10 recognition audit

`tools/atm10/recognition-audit.sh` opens an ATM10 save, runs `/zoo debug recognise audit` and closes the game (about 7 minutes; don't open the pause menu while it runs, as a paused single-player world stops ticking). The audit builds a stone platform 2048 blocks east of spawn at y=240, makes a temporary zoo on it, and spawns every mob type there one per tick, the way `/summon` does. For each one it checks the verdict from the type, the verdict for the spawned mob, whether the real census counts it, and whether a counted animal is made persistent, then removes it and anything its spawn brought along. Results go to `run-atm10/logs/zoocraft-recognition-audit.csv`, and `tools/atm10/check_recognition.py` compares them with the reviewed `tools/atm10/recognition-expected.csv` (one line per entity type, with notes on the judgement calls). The check fails on any changed verdict, any problem in the audit or any new entity type, so it is the test to rerun after an ATM10 update: review the differences, then accept them with `--accept`.

Results for ATM10 8.2 (2026-10-16): 1,417 entity types, 787 of them mobs, 217 recognised. The type check, the spawned mob, the census and persistence agreed for every mob. The mods named in the Phase 3 spec:

| Mod | Recognised | Not recognised (and why) |
|---|---|---|
| Twilight Forest | 9: bighorn sheep, boar, deer, dwarf rabbit, penguin, questing ram, raven, squirrel, tiny bird | 9 bosses (boss tag), 41 hostile mobs (monster category) |
| The Aether | 6: aerbunny, aerwhale (tagged in: it uses the Aether's own spawn category), flying cow, moa, phyg, sheepuff | 3 bosses; zephyrs, swets, whirlwinds, cockatrices, aechor plants and other hostiles |
| Aquaculture | all 32: fish, turtles, jellyfish and shroomas | none |
| The Undergarden | 9: brute, dweller, gloomper, greater dweller, gwib, gwibling, mog, scintling, s'mog | the forgotten guardian (boss), the stoneborn (trader), the mysterious pot (tagged out: a pot in the creature category), 10 hostiles |
| Productive Bees | all 24 bee types | none |
| MineColonies | the cavalry horse | citizens, visitors and mercenaries (tagged out; `minecolonies:citizen` is the right id), 36 raiders (monster category) |
| Ice and Fire | 8: amphithere, cockatrice, death worm, hippocampus, hippogryph, pixie, sea serpent, stymphalian bird | the three dragons, the cyclops, gorgon, siren and hydra, and dragon and mob skulls (tagged out); 9 hostiles |
| L_Ender's Cataclysm | none | 17 bosses (boss tag, including the baby leviathan), the netherite ministrosity (tagged out: a tameable construct), 21 hostiles |

Other ATM10 mods with recognised creatures: Living Things (18, including elephants, giraffes, lions and sharks), Eternal Starlight (12), Shiny (38, merged into the vanilla species), the Bumblezone (4), Ars Nouveau (6: wild starbuncles, drygmies, whirlisprigs, alakarkinos, and the Lily and Nook pets), Ars Elemental (flashjack, siren), Auroral, Oh The Biomes We've Gone, Deeper and Darker, Ad Astra (glacian ram), Mahou Tsukai (fae), Dyenamics and Sushi Go Crafting.

Tagged out in ATM10 (114 entity types in all): familiars and summoned or conjured creatures (Ars Nouveau, Ars Elemental, Iron's Spells, Occultism, Neovitae, Mahou Tsukai), Occultism's spirits, Ars Nouveau's weald walkers, golems and animated blocks, Eternal Starlight's golems, boarwarf and zombified ratlin, PneumaticCraft's drones, Enderman Overhaul's pet and passive endermen, Forbidden Arcanus's lost souls and dark trader, Tombstone's guardians, Creeper Overhaul's mushroom creeper, Ad Astra's lunarians and the Supplementaries red merchant.

## Known gaps

- **Recognition follows the entity type, not the individual.** Productive Bees registers one `configurable_bee` type for all its configurable species, so they count as one species. A tamed or named mob of a recognised type is still an exhibit animal.
- **Judgement calls in recognition** (familiars, summons, magical helpers) are recorded in `tools/atm10/recognition-expected.csv`; a modpack that disagrees can change them with the two tags. Magical creatures that live in the wild, such as starbuncles and Ice and Fire's pixies, are recognised; summoned and familiar forms are not.
- **The audit spawns each mob in the open on stone**, so a mob that changes type or removes itself when spawned in the wrong place would count as not spawned; none did in ATM10 8.2.
- **The animal census itself is not saved.** It is rebuilt the first time it is needed after a restart. Exhibit view counts are saved with the zoo and matched back to their exhibits by the animals' UUIDs, so they carry over; an exhibit whose animals have all been replaced starts again from zero.
- **Zoos only run in loaded chunks** unless "Keep zoo loaded" is on (off by default, capped by `maxLoadedChunksPerZoo`).
- **FTB Chunks claims** were not tested against visitor movement. Claims protect blocks rather than mob movement, so no conflict is expected.
- **Animals count at any height** inside the boundary (Phase 3); the census searches only the boundary's columns.
- **Litter is an entity, not an item.** Item magnets, hoppers and vacuum mods ignore it, which is intended. Commands and mods that kill all entities (for example `/kill @e` or entity-clearing lag tools) remove litter too, which simply cleans the zoo.
- **Shop stock containers are found through NeoForge's item handler capability.** Storage blocks that expose it (chests, barrels, Sophisticated Storage, drawers) can be linked; ones that don't can't. Hoppers and pipes can still move items in and out of linked containers.
- **Commands can't fill every modded container.** `/item replace` doesn't reach Sophisticated Storage inventories, so scripted checks fill them with a hopper instead. Visitors and the dashboard are unaffected, because they go through the item handler capability.
- **Shop stock counts only loaded containers.** A container in an unloaded chunk counts as empty and the Shops tab says some stock is in an unloaded area.
