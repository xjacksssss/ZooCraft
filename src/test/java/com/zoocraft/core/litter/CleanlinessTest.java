package com.zoocraft.core.litter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanlinessTest {
    @Test
    void sampleFallsByTwentyPerUnit() {
        assertEquals(100, Cleanliness.sample(0), 1e-9);
        assertEquals(60, Cleanliness.sample(2), 1e-9);
        assertEquals(0, Cleanliness.sample(6), 1e-9);
    }

    @Test
    void blendMovesFivePercentOfTheWay() {
        assertEquals(95, Cleanliness.blend(100, 0), 1e-9);
        assertEquals(52.5, Cleanliness.blend(50, 100), 1e-9);
    }

    @Test
    void labels() {
        assertEquals("clean", Cleanliness.label(80));
        assertEquals("some_litter", Cleanliness.label(79.9));
        assertEquals("some_litter", Cleanliness.label(50));
        assertEquals("dirty", Cleanliness.label(49.9));
    }
}
