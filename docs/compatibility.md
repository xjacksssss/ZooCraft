# ZooCraft compatibility notes

Known gaps are recorded here rather than hidden (GDD section 5.4).

## Test environment

- ATM10 8.2 (NeoForge 21.1.251, 491 mods), mods and configs copied into `run-atm10` by `tools/sync-atm10-mods.ps1`.
- Run with `./gradlew runAtm10Client -Patm10`. The `-Patm10` flag makes ModDevGradle use the original Minecraft jar instead of a recompiled one. Without it, mods whose mixins target synthetic lambda names (for example Accessories' `EntityRenderersMixin`) fail to apply, which is a dev-environment artefact rather than a real conflict.

## Results

| Date | Milestone | Result |
|---|---|---|
| 2026-10-14 | M0 | ZooCraft 0.1.0 loads with all ATM10 mods and reaches the title screen. SmartBrainLib 1.16.11 is shared with ATM10's copy; no duplicate-mod error. |

## Known gaps

None recorded yet.
