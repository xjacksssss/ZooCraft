package com.zoocraft.visitor;

/** When visitors try to cut corners on their path (Phase 3 spec section 7.1). */
public enum ShortcutMode {
    /** When the visitor reaches a new path node or gets a new path. */
    EVERY_NODE,
    /** Every tick: SmartBrainLib's own behaviour. */
    EVERY_TICK,
    /** Never: follow the path node by node. */
    OFF
}
