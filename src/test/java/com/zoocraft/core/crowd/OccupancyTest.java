package com.zoocraft.core.crowd;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupancyTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    @Test
    void claimsUpToCapacityAndRenews() {
        Occupancy<String> o = new Occupancy<>(100);
        assertTrue(o.claim("spot", A, 0, 2));
        assertTrue(o.claim("spot", B, 0, 2));
        assertFalse(o.claim("spot", C, 0, 2));
        assertTrue(o.claim("spot", A, 50, 2), "a holder can always renew");
        assertEquals(2, o.count("spot", 50));
    }

    @Test
    void releaseAndReleaseAllFreePlaces() {
        Occupancy<String> o = new Occupancy<>(100);
        o.claim("x", A, 0, 2);
        o.claim("y", A, 0, 2);
        o.claim("x", B, 0, 2);
        o.release("x", B);
        assertEquals(1, o.count("x", 0));
        o.releaseAll(A);
        assertEquals(0, o.count("x", 0));
        assertEquals(0, o.count("y", 0));
        assertFalse(o.holds("y", A));
    }

    @Test
    void unrenewedClaimsExpire() {
        Occupancy<String> o = new Occupancy<>(100);
        o.claim("spot", A, 0, 1);
        assertFalse(o.claim("spot", B, 100, 1), "still held at exactly the expiry");
        assertTrue(o.claim("spot", B, 101, 1));
        assertFalse(o.holds("spot", A));
    }

    @Test
    void loweredCapacityKeepsHoldersButAdmitsNoOne() {
        Occupancy<String> o = new Occupancy<>(100);
        o.claim("spot", A, 0, 2);
        o.claim("spot", B, 0, 2);
        assertTrue(o.claim("spot", A, 1, 1));
        assertFalse(o.claim("spot", C, 1, 1));
        assertEquals(2, o.maxCount(1));
    }
}
