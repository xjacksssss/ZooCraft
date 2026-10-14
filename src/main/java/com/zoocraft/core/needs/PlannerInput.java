package com.zoocraft.core.needs;

/**
 * What the planner knows about a visitor and its zoo. "Available" means reachable, open and suitable according to
 * the cached facility snapshot.
 *
 * @param leaving        the visit is already ending (closing, safety net, boundary release)
 * @param foodOnCooldown the visitor recently failed to buy food and won't try again yet
 * @param endingByChoice the visitor wants to go home (interest, time or money), so a last stop may happen
 * @param lastStopDone   the souvenir last stop has already happened (or was attempted)
 */
public record PlannerInput(boolean leaving, boolean hungry, boolean foodAvailable, boolean needsToilet, boolean toiletAvailable,
                           boolean foodOnCooldown, boolean toiletOnCooldown, boolean endingByChoice, boolean souvenirWish,
                           boolean souvenirAvailable, boolean lastStopDone) {
}
