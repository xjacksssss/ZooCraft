package com.zoocraft.core.crowd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who holds a place at each spot (Phase 3 spec section 5.1): viewing spots, gate queue slots, counters and toilets.
 * A claim lasts {@code expiryTicks} unless renewed, so a holder that vanishes never blocks a spot for long. Used on the
 * server thread only.
 */
public final class Occupancy<K> {
    private final long expiryTicks;
    private final Map<K, Map<UUID, Long>> holders = new HashMap<>();
    private final Map<UUID, Set<K>> heldBy = new HashMap<>();

    public Occupancy(long expiryTicks) {
        this.expiryTicks = expiryTicks;
    }

    /** Takes or renews a place. A holder may always renew; others only while fewer than {@code capacity} hold it. */
    public boolean claim(K spot, UUID who, long now, int capacity) {
        Map<UUID, Long> at = holders.computeIfAbsent(spot, k -> new HashMap<>());
        expire(spot, at, now);
        if (!at.containsKey(who) && at.size() >= capacity) {
            if (at.isEmpty()) {
                holders.remove(spot);
            }
            return false;
        }
        at.put(who, now);
        heldBy.computeIfAbsent(who, k -> new HashSet<>()).add(spot);
        return true;
    }

    public void release(K spot, UUID who) {
        Map<UUID, Long> at = holders.get(spot);
        if (at != null) {
            at.remove(who);
            if (at.isEmpty()) {
                holders.remove(spot);
            }
        }
        Set<K> spots = heldBy.get(who);
        if (spots != null) {
            spots.remove(spot);
            if (spots.isEmpty()) {
                heldBy.remove(who);
            }
        }
    }

    public void releaseAll(UUID who) {
        Set<K> spots = heldBy.remove(who);
        if (spots == null) {
            return;
        }
        for (K spot : spots) {
            Map<UUID, Long> at = holders.get(spot);
            if (at != null) {
                at.remove(who);
                if (at.isEmpty()) {
                    holders.remove(spot);
                }
            }
        }
    }

    public int count(K spot, long now) {
        Map<UUID, Long> at = holders.get(spot);
        if (at == null) {
            return 0;
        }
        expire(spot, at, now);
        return at.size();
    }

    public boolean holds(K spot, UUID who) {
        Map<UUID, Long> at = holders.get(spot);
        return at != null && at.containsKey(who);
    }

    /** The most holders on any one spot. */
    public int maxCount(long now) {
        int most = 0;
        for (Map.Entry<K, Map<UUID, Long>> entry : holders.entrySet()) {
            expire(entry.getKey(), entry.getValue(), now);
            most = Math.max(most, entry.getValue().size());
        }
        return most;
    }

    public void clear() {
        holders.clear();
        heldBy.clear();
    }

    private void expire(K spot, Map<UUID, Long> at, long now) {
        at.entrySet().removeIf(e -> {
            if (now - e.getValue() <= expiryTicks) {
                return false;
            }
            Set<K> spots = heldBy.get(e.getKey());
            if (spots != null) {
                spots.remove(spot);
                if (spots.isEmpty()) {
                    heldBy.remove(e.getKey());
                }
            }
            return true;
        });
    }
}
