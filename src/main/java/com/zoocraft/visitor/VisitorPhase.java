package com.zoocraft.visitor;

public enum VisitorPhase {
    /** Walking from the spawn point to the gate's outside point. */
    ARRIVING,
    /** At the gate, waiting for the rest of the group and the admission decision. */
    QUEUEING,
    /** Admitted and looking around inside the zoo. */
    EXPLORING,
    /** Walking to an exit gate. */
    LEAVING,
    /** Through the gate (or outside the zoo), walking away before fading out. */
    EXITING,
    /** Turned away at the gate, walking away before fading out. */
    REFUSED
}
