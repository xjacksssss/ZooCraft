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

## Recognition defaults

- Recognised automatically: anything extending vanilla `Animal` or `WaterAnimal`, or registered in the creature, water creature, water ambient, underground water creature or axolotl spawn categories. Modpacks can add more with the `zoocraft:exhibit_animals` entity type tag.
- Never recognised: `c:bosses` (Cataclysm tags its bosses this way), the monster spawn category unless tagged in, and `zoocraft:not_exhibit_animals`, which ships with villagers, wandering traders, iron and snow golems, ZooCraft visitors, and optional entries for `minecolonies:citizen`, `minecolonies:visitor` and the three Ice and Fire dragons.
- Separate entity types that are really one animal can be merged with the `zoocraft:species` data map.

## Known gaps

- **Recognition in ATM10 animal mods is not yet checked creature by creature.** The ATM10 run used vanilla animals. Twilight Forest, Aether, Aquaculture, Undergarden and Productive Bees creatures should be recognised by the default rules, and MineColonies citizens and dragons should be excluded, but this needs an in-world check (planned for Phase 3, GDD section 11).
- **The MineColonies citizen entity id is assumed** to be `minecolonies:citizen`; the tag entry is optional, so a wrong id does no harm, and citizens are not `Animal`s or creature-category mobs anyway.
- **Exhibit view counts and the animal census are not saved.** They rebuild within one census interval after a restart; view counts start again from zero.
- **Zoos only run in loaded chunks** unless "Keep zoo loaded" is on (off by default, capped by `maxLoadedChunksPerZoo`).
- **FTB Chunks claims** were not tested against visitor movement. Claims protect blocks rather than mob movement, so no conflict is expected.
- **Animals are searched within 48 blocks below and 64 above** the zoo's corners and gates, so very deep or very high enclosures are not counted.
- **Litter is an entity, not an item.** Item magnets, hoppers and vacuum mods ignore it, which is intended. Commands and mods that kill all entities (for example `/kill @e` or entity-clearing lag tools) remove litter too, which simply cleans the zoo.
- **Shop stock containers are found through NeoForge's item handler capability.** Storage blocks that expose it (chests, barrels, Sophisticated Storage, drawers) can be linked; ones that don't can't. Hoppers and pipes can still move items in and out of linked containers.
