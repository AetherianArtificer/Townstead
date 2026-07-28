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
     * Half-width of a rasterised circle at row {@code dy}. Every circular edge here goes through
     * this, so a disc and the hole cut for it land on the same pixels instead of on two roundings
     * that disagree by a pixel or two and leave the background showing through the seam.
     */
    static int halfAt(int radius, int dy) {
        double unit = (Math.abs(dy) + 0.5) / radius;
        if (unit >= 1) return 0;
        return (int) Math.round(radius * Math.sqrt(1 - unit * unit));
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
            int span = halfAt(outer, dy);
            if (span <= 0) continue;
            int solid = halfAt(inner, dy);
            if (solid <= 0) {
                paintRow(g, cx, cy, dy, -span, span, sectors, step, painter);
            } else {
                paintRow(g, cx, cy, dy, -span, -solid - 1, sectors, step, painter);
                paintRow(g, cx, cy, dy, solid + 1, span, sectors, step, painter);
            }
        }
    }

    private static void paintRow(GuiGraphics g, int cx, int cy, int dy, int from, int to,
                                 int sectors, double step, RingPainter painter) {
        if (from > to) return;
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

    /** A filled circle as rows, symmetric about both axes so it can be centred on a pixel. */
    static void disc(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = halfAt(radius, dy);
            if (half > 0) g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /**
     * The cancel well, sized to fill the dial's hole. Edge is darker than anything on the face: a
     * light rim reads as a badge stuck on the dial rather than an opening in it.
     */
    static void hub(GuiGraphics g, int cx, int cy, int radius, boolean armed) {
        disc(g, cx, cy, radius, 0xFF120D07);
        disc(g, cx, cy, radius - 1, armed ? 0xFF2E2314 : 0xFF191208);
    }

    /**
     * Release here and nothing is cast. Only drawn while the cursor is resting in the hub.
     *
     * <p>Spelled out rather than looped, because a loop stepping a run of two along a diagonal is a
     * pixel wider on one side than the other and cannot be centred on the well it marks.</p>
     */
    private static final String[] CANCEL = {
            "110000011", "011000110", "001101100", "000111000", "000010000",
            "000111000", "001101100", "011000110", "110000011"};

    /** Centred on {@code cx, cy}. */
    static void cancelMark(GuiGraphics g, int cx, int cy, int color) {
        for (int row = 0; row < CANCEL.length; row++) {
            for (int col = 0; col < CANCEL[row].length(); col++) {
                if (CANCEL[row].charAt(col) == '1') {
                    g.fill(cx - 4 + col, cy - 4 + row, cx - 3 + col, cy - 3 + row, color);
                }
            }
        }
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
