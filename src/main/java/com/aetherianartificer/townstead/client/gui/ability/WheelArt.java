package com.aetherianartificer.townstead.client.gui.ability;

import com.aetherianartificer.townstead.client.gui.common.Palette;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The dial's own drawing kit: discs, annulus scanlines, tiny numerals and the wax boss.
 *
 * <p>Everything here fills each pixel EXACTLY ONCE. The first wheel drew its wedges as a fan of
 * radial spokes, which overlap badly toward the middle; with any translucency each overlap
 * composites again and the result was a crosshatch moiré that looked like a broken texture.
 * Scanlines cannot overlap, and they cost about a tenth of the quads.</p>
 */
final class WheelArt {

    private WheelArt() {}

    /** What one pixel of the dial should be, or 0 to leave it alone. */
    interface RingPainter {
        int colorAt(int sector, double within, int radius);
    }

    /**
     * Paints an annulus by rows, asking {@link RingPainter} per pixel and filling in runs.
     *
     * @param within where the pixel falls across its sector, 0 at one edge and 1 at the other
     */
    static void paintRing(GuiGraphics g, int cx, int cy, int outer, int inner, int sectors,
                          RingPainter painter) {
        double step = 2 * Math.PI / sectors;
        for (int dy = -outer; dy <= outer; dy++) {
            int span = (int) Math.floor(Math.sqrt(Math.max(0, outer * outer - dy * dy)));
            if (span <= 0) continue;
            int hole = Math.abs(dy) < inner
                    ? (int) Math.ceil(Math.sqrt(inner * inner - dy * dy)) : 0;
            if (hole == 0) {
                paintRow(g, cx, cy, dy, -span, span, sectors, step, painter);
            } else {
                paintRow(g, cx, cy, dy, -span, -hole, sectors, step, painter);
                paintRow(g, cx, cy, dy, hole, span, sectors, step, painter);
            }
        }
    }

    private static void paintRow(GuiGraphics g, int cx, int cy, int dy, int from, int to,
                                 int sectors, double step, RingPainter painter) {
        int runStart = from;
        int runColor = 0;
        for (int dx = from; dx <= to; dx++) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
            while (angle < 0) angle += 2 * Math.PI;
            while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
            double position = angle / step;
            int sector = (int) position % sectors;
            int radius = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
            int color = painter.colorAt(sector, position - Math.floor(position), radius);
            if (dx == from) {
                runColor = color;
                continue;
            }
            if (color != runColor) {
                if (runColor != 0) g.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, runColor);
                runStart = dx;
                runColor = color;
            }
        }
        if (runColor != 0) g.fill(cx + runStart, cy + dy, cx + to + 1, cy + dy + 1, runColor);
    }

    /** A filled circle as rows, matching the pixel edge the rest of the family draws with. */
    static void disc(GuiGraphics g, int cx, int cy, int radius, int color) {
        if (radius <= 0) return;
        for (int dy = -radius; dy < radius; dy++) {
            double unit = (dy + 0.5) / radius;
            int half = (int) Math.round(radius * Math.sqrt(Math.max(0d, 1d - unit * unit)));
            if (half > 0) g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    /**
     * The hub: a recessed well, not an ornament.
     *
     * <p>It was a wax seal, which looked like a stop sign in the middle of an instrument you are
     * meant to be reading past. The centre of a dial is where the cursor RESTS, so it wants to be
     * quiet and to read as empty space you can safely let go in.</p>
     */
    static void hub(GuiGraphics g, int cx, int cy, int radius) {
        disc(g, cx, cy, radius, 0xFF14100A);
        disc(g, cx, cy, radius - 1, 0xFF1C1509);
        g.fill(cx - radius + 3, cy - radius + 1, cx + radius - 3, cy - radius + 2, 0xFF0F0B06);
    }

    /**
     * A switch: this one is held ON or OFF rather than cast.
     *
     * <p>Carries the KIND and the STATE in one mark. A toggle and a one-shot behave nothing alike,
     * and the arc already distinguishes them once you know the rule, which is exactly the sort of
     * thing a player should not have to be told.</p>
     */
    static void switchMark(GuiGraphics g, int x, int y, boolean on) {
        g.fill(x, y, x + 8, y + 5, 0xFF1A1208);
        g.fill(x + 1, y + 1, x + 7, y + 4, on ? 0xFF4A3A20 : 0xFF241A0E);
        int knob = on ? x + 4 : x + 1;
        g.fill(knob, y + 1, knob + 3, y + 4, on ? Palette.BRASS_HOT : 0xFF6E6350);
    }

    /** A spark: this one is cast once and then cools. */
    static void sparkMark(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 3, y + 5, color);
        g.fill(x, y + 2, x + 5, y + 3, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + 3, y + 3, x + 4, y + 4, color);
    }

    /** A recessed hole: somewhere an ability GOES, rather than a frame with a dash in it. */
    static void socket(GuiGraphics g, int cx, int cy, int size) {
        int x = cx - size / 2;
        int y = cy - size / 2;
        g.fill(x, y, x + size, y + size, 0xFF171009);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF1E1509);
        g.fill(x + 1, y + 1, x + size - 1, y + 2, 0xFF100B06);
        g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, 0xFF3A2E1E);
    }

    // ── Numerals ───────────────────────────────────────────────────────────
    //
    // Three by five, drawn rather than typed. The game font is nine pixels tall, which does not fit
    // in the corner of a twenty-six pixel frame without covering the icon it is labelling.

    private static final String[][] DIGITS = {
            {"111", "101", "101", "101", "111"}, {"010", "110", "010", "010", "111"},
            {"111", "001", "111", "100", "111"}, {"111", "001", "111", "001", "111"},
            {"101", "101", "111", "001", "001"}, {"111", "100", "111", "001", "111"},
            {"111", "100", "111", "101", "111"}, {"111", "001", "001", "001", "001"},
            {"111", "101", "111", "101", "111"}, {"111", "101", "111", "001", "111"}};

    /** One digit, top-left anchored. Values above nine draw as their last digit. */
    static void digit(GuiGraphics g, int x, int y, int value, int color) {
        String[] rows = DIGITS[Math.floorMod(value, 10)];
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < 3; col++) {
                if (rows[row].charAt(col) == '1') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }

    /** A small number, right-anchored, for seconds remaining. */
    static void number(GuiGraphics g, int right, int y, int value, int color) {
        int digits = value >= 10 ? 2 : 1;
        int x = right - digits * 4 + 1;
        if (digits == 2) digit(g, x, y, value / 10, color);
        digit(g, x + (digits == 2 ? 4 : 0), y, value % 10, color);
    }
}
