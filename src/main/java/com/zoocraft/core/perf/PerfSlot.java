package com.zoocraft.core.perf;

/** What ZooCraft times (Phase 3 spec section 6.1). Order is the CSV column order. */
public enum PerfSlot {
    SERVER_TICK("server tick"),
    ZOOCRAFT_TOTAL("ZooCraft total"),
    VISITOR_CONTROLLER("visitor decisions"),
    VISITOR_BRAIN("visitor brain"),
    VISITOR_NAVIGATION("visitor path following"),
    VISITOR_PATHFINDING("visitor path planning"),
    ANIMAL_CENSUS("animal census"),
    FACILITY_CENSUS("facility census"),
    ENCLOSURE("enclosure checks"),
    SPAWNER("visitor spawner"),
    DASHBOARD("dashboard"),
    LITTER("litter"),
    CHUNK_LOADER("chunk loading");

    private final String label;

    PerfSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
