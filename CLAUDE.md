# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

ZooCraft is a Minecraft **1.21.1 / NeoForge** zoo-building and management mod, built as a standalone mod that must also run in the **All the Mods 10 (ATM10)** (NeoForge) modpack.

The project lives at `C:\dev\ZooCraft` (deliberately outside OneDrive) and is a git repository. It currently contains the design document, [zoocraft_game_design_document_v1.0.md](zoocraft_game_design_document_v1.0.md), and the Phase 1 prototype spec, [docs/superpowers/specs/2026-10-14-phase1-prototype-design.md](docs/superpowers/specs/2026-10-14-phase1-prototype-design.md). There is no source code or build system yet. When the mod project is scaffolded (milestone M0), add the real build, run, and test commands here. Do not assume any.

The GDD is the source of truth for gameplay. Section 12 ("Locked design decisions") lists decisions that should not be reopened unless testing shows a real problem. The Phase 1 spec turns GDD section 14 into rules, edge cases and acceptance tests, fixes the architecture and libraries (NeoForge 21.1.251, SmartBrainLib, JTS, built-in NeoForge systems), and splits the work into milestones M0 to M4, each with its own implementation plan in `docs/superpowers/plans/`.

## Scope discipline

Development is phased (GDD section 11). The first milestone (section 14) is a tiny playable prototype: a custom boundary with at least two gates, open/close with an emerald admission price, villagers that arrive, pay, walk to animals and leave, automatic animal recognition with nearby viewing, a small dashboard, and shared two-player state.

Do not build these until their phase: staff and wages, breeding and conservation, complex per-species welfare, elaborate shops, rides and tours, unlock trees or research. Shops, toilets, and litter belong to Phase 2, not the first prototype.

## Design constraints that shape the architecture

- **A zoo is a server-side data object, separate from the blocks inside it.** It holds one owner, a list of managers, a custom-shaped boundary, multiple gates, a treasury, admission price, reputation, and records of exhibits, shops, and facilities. Editing the boundary must never delete structures or financial progress. Overlapping boundaries are rejected. One player can own several zoos, each with its own finances.
- **"Inside the boundary" and "reachable" are different things.** Facilities, exhibits, and animal viewing count only when visitors can actually reach them. An isolated building inside the boundary does not count.
- **Exactly-once transactions.** Admission and shop sales must move emeralds and stock exactly once. A failed purchase changes neither. Admission is charged once per visit, not once per gate. The MVP test suite explicitly checks for duplicated payments, duplicated stock removal, and lost zoo data.
- **Multiplayer from day one.** The owner and managers share one treasury and see consistent state. The zoo must keep working while the owner is offline. Only the owner can invite or remove managers or transfer ownership (permission table in section 3.4).
- **Visitors are real walking villager entities**, arriving alone or in groups. Each has one of three profiles (budget-conscious, animal enthusiast, general) and an individual emerald budget that is kept separate from the treasury. Visitors must never get permanently trapped, including when the zoo closes, a gate is blocked, or the boundary changes.
- **Animal recognition is automatic** across vanilla and modded animals, with no hard dependency on any animal mod. Do not treat every living entity as an exhibit animal. When an animal's requirements are unknown, do not invent strict ones. Trivial variants such as babies or alternate forms should not inflate species variety.
- **Viewing is proximity-based**: an animal counts as viewed when a visitor gets close enough in an accessible area. Full line-of-sight checks are not required, but animals behind walls or far off the visitor's route must not count.
- **Welfare only produces warnings and lower ratings.** No automatic animal deaths. Warnings and dashboard messages should name the cause in plain language ("visitors can't get close enough to see the animals"), not just report a score.
- **Players design buildings freely.** Shops, toilets, and gates are designations applied with a tool to ordinary blocks. Rubbish bins are the only dedicated zoo block.
- **Performance comes before crowd size.** Use modest, configurable visitor limits. Cache and reuse visitor decisions. Never scan every animal and block for every visitor. Cap litter and visitor accumulation. Satisfaction and reputation should change gradually, never collapse because of one visitor.
- **Compatibility gaps get documented**, not hidden.

## Language

The GDD uses British English ("recognise", "rubbish", "colour"). Use British spelling in player-facing text and docs.
