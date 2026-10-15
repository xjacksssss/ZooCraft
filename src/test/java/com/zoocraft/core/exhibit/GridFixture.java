package com.zoocraft.core.exhibit;

/**
 * A {@link FloorGrid} drawn as text: {@code .} floor at height 0, digits floor at that height, {@code #} a fence (no
 * floor), {@code o} an open gate (floor). Column x is the character, z the row. Outside the drawing is open floor at
 * height 0, so a fill that escapes runs on to its cap.
 */
final class GridFixture implements FloorGrid {
    private final String[] rows;

    GridFixture(String... rows) {
        this.rows = rows;
    }

    int height(int x, int z) {
        if (z < 0 || z >= rows.length || x < 0 || x >= rows[z].length()) {
            return 0;
        }
        char c = rows[z].charAt(x);
        if (c == '#') {
            return NO_FLOOR;
        }
        return Character.isDigit(c) ? c - '0' : 0;
    }

    @Override
    public int stepTo(int x, int z, int fromY) {
        int h = height(x, z);
        return h != NO_FLOOR && Math.abs(h - fromY) <= 1 ? h : NO_FLOOR;
    }

    /** A fenced pen with a {@code w} by {@code d} floor starting at (1, 1). */
    static String[] pen(int w, int d) {
        String[] rows = new String[d + 2];
        rows[0] = "#".repeat(w + 2);
        rows[d + 1] = rows[0];
        for (int z = 1; z <= d; z++) {
            rows[z] = "#" + ".".repeat(w) + "#";
        }
        return rows;
    }
}
