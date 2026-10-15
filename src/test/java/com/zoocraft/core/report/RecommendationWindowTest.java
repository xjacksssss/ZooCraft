package com.zoocraft.core.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationWindowTest {
    @Test
    void keepsOnlyTheLastHundred() {
        RecommendationWindow window = new RecommendationWindow();
        for (int i = 0; i < 100; i++) {
            window.add(90);
        }
        for (int i = 0; i < 30; i++) {
            window.add(10);
        }
        assertEquals(100, window.size());
        assertEquals(70, window.recommended());
    }

    @Test
    void seventyRecommendsButJustUnderDoesNot() {
        RecommendationWindow window = new RecommendationWindow();
        window.add(70);
        window.add(69.9);
        assertEquals(2, window.size());
        assertEquals(1, window.recommended());
    }

    @Test
    void encodingRoundTrips() {
        RecommendationWindow window = new RecommendationWindow();
        window.add(80);
        window.add(20);
        window.add(75);
        RecommendationWindow back = RecommendationWindow.decode(window.encode());
        assertEquals(window.encode(), back.encode());
        assertEquals(2, back.recommended());
    }
}
