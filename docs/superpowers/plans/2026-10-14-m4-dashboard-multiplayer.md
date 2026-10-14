# M4 Dashboard and Multiplayer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A small dashboard (Overview, Animals, Settings) that the owner and managers share, kept in sync by the server, plus debug commands and the final standalone, two-player and ATM10 checks (spec section 9).

**Architecture:** The server builds a `DashboardSnapshot` from `ZooManager`, `VisitorTracker`, `AnimalCensus` and `ZooAlerts`, and pushes it about once a second to subscribed players only. The client screen only displays snapshots and sends `DashboardActionPayload` requests, which the server runs through the same `ZooManager` methods as the commands. Clients never change zoo data.

**Tech Stack:** NeoForge payloads with hand-written `StreamCodec`s, `ComponentSerialization` for text, vanilla screens and widgets, `RegisterKeyMappingsEvent`.

**Spec:** `docs/superpowers/specs/2026-10-14-phase1-prototype-design.md` (section 9)

**Execution note:** The user asked for uninterrupted execution.

## Global Constraints

- All dashboard changes go through `ZooManager`; permissions are checked on the server.
- Snapshots go only to subscribed players who still have VIEW permission.
- Default dashboard key: semicolon (unbound in ATM10's key layout), rebindable.

## Review Focus

- A manager removed while their dashboard is open: the next refresh closes it and further requests are refused (GameTest on `DashboardService` permission check).
- Two simultaneous withdrawals with funds for one: exactly one succeeds (covered by the main-thread payload handling and ledger rule; GameTest calls the action handler twice).
- Typing non-numbers into price or amount boxes: the client ignores invalid input; the server range-checks again.
- A player with several zoos and none underfoot: the picker lists them.
- A player with no zoos: a chat message explains how to start one.

---

### Task 1: Snapshot, payloads and server service

**Files:**
- Create: `network/DashboardSnapshot.java`, `network/DashboardPayloads.java` (OpenDashboardRequest, ZooListPayload, DashboardActionPayload, CloseDashboardPayload, DashboardClosedPayload)
- Create: `dashboard/DashboardService.java`, `dashboard/DashboardAction.java`
- Modify: `network/ZooNetwork.java`
- Test: `gametest/DashboardGameTests.java`

**Produces:** `DashboardService.open(ServerPlayer, Optional<UUID>)`, `DashboardService.handle(ServerPlayer, DashboardActionPayload) -> ActionResult`, `DashboardService.snapshot(ServerPlayer, Zoo) -> DashboardSnapshot`, subscriber refresh every 20 ticks.

### Task 2: Client screens and keybind

**Files:**
- Create: `client/DashboardScreen.java`, `client/ZooPickerScreen.java`, `client/ClientDashboardState.java`, `client/ZooKeys.java`
- Modify: `client/ClientPayloadHandlers.java`, `client/ZooCraftClient.java`, lang file

### Task 3: Commands, debug tools and final checks

**Files:**
- Modify: `command/ZooCommands.java` (`/zoo` opens the dashboard; `/zoo debug create|gate|spawn|census|visitors`)
- Create: `docs/playtests/m4-dashboard-multiplayer.md`, update `docs/compatibility.md`, `CLAUDE.md`

- [ ] Standalone production jar on a real NeoForge 21.1.251 dedicated server with SmartBrainLib: loads, JTS available.
- [ ] Scripted visual check with a dev server and client: visitors render, outlines draw, dashboard opens.
- [ ] ATM10 copy: loads and the loop works.
