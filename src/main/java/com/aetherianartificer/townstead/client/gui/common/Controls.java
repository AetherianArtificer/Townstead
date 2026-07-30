package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The small controls Townstead screens are assembled from: segmented pickers, steppers, status
 * chips, pills, row tools and item slots.
 *
 * <p>Every control here is laid out and drawn through the <em>same</em> {@link Rect}. That is the
 * whole point of the class: a control whose drawing computes one rectangle and whose click handler
 * computes another looks correct until the font metrics change, and then it is a bug nobody can
 * see. Callers lay out once, keep the rects, and pass them to both the draw call and the hit
 * test.</p>
 */
public final class Controls {

    private Controls() {}

    /** A laid-out box. Exclusive of the right and bottom edge, like {@code GuiGraphics.fill}. */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    /** Small uppercase label above a control, in the family's field-label voice. */
    public static void fieldLabel(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text.toUpperCase(java.util.Locale.ROOT), x, y, Palette.INK_DIM, false);
    }

    // ── Segmented picker ──

    private static final int SEG_PAD = 5;
    public static final int SEG_H = 13;

    /** One rect per option, laid left to right. Width follows the labels, so nothing is clipped. */
    public static Rect[] segmentLayout(Font font, int x, int y, String... labels) {
        Rect[] out = new Rect[labels.length];
        int cursor = x;
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + SEG_PAD * 2;
            out[i] = new Rect(cursor, y, w, SEG_H);
            cursor += w;
        }
        return out;
    }

    /**
     * Draws a segmented picker. The chosen segment is parchment with dark ink on it, which is the
     * family's way of saying "this one", and reads at a glance without a tick or a highlight ring.
     */
    public static void drawSegments(GuiGraphics g, Font font, Rect[] rects, String[] labels,
                                    int selected, int hovered) {
        for (int i = 0; i < rects.length; i++) {
            Rect r = rects[i];
            boolean on = i == selected;
            g.fill(r.x(), r.y(), r.right(), r.bottom(),
                    on ? Palette.CARD : i == hovered ? Palette.DESK : Palette.ROW);
            g.drawString(font, labels[i], r.x() + SEG_PAD, r.y() + 3,
                    on ? Palette.CARD_INK : i == hovered ? Palette.LABEL_LIGHT : Palette.LABEL_MID,
                    false);
            // One shared rule between segments rather than a box each, so they read as one control.
            if (i > 0) g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), Palette.DESK_LIP);
        }
        if (rects.length > 0) {
            Palette.drawOutline(g, rects[0].x(), rects[0].y(),
                    rects[rects.length - 1].right(), rects[0].bottom(), Palette.DESK_LIP);
        }
    }

    /** The segment under the cursor, or -1. */
    public static int segmentAt(Rect[] rects, double mx, double my) {
        for (int i = 0; i < rects.length; i++) {
            if (rects[i].contains(mx, my)) return i;
        }
        return -1;
    }

    // ── Stepper ──

    /** {@code [−][ value ][+]}, laid out around a value plate wide enough for the number. */
    public static Rect[] stepperLayout(Font font, int x, int y, String value) {
        int arrow = 11;
        int plate = Math.max(20, font.width(value) + 8);
        return new Rect[]{
                new Rect(x, y, arrow, SEG_H),
                new Rect(x + arrow, y, plate, SEG_H),
                new Rect(x + arrow + plate, y, arrow, SEG_H)
        };
    }

    public static void drawStepper(GuiGraphics g, Font font, Rect[] rects, String value,
                                   boolean enabled, int hovered) {
        drawArrow(g, font, rects[0], "-", enabled, hovered == 0);
        Rect plate = rects[1];
        g.fill(plate.x(), plate.y(), plate.right(), plate.bottom(),
                enabled ? Palette.CARD : Palette.ROW);
        g.drawString(font, value, plate.x() + (plate.w() - font.width(value)) / 2, plate.y() + 3,
                enabled ? Palette.CARD_INK : Palette.LABEL_DIM, false);
        drawArrow(g, font, rects[2], "+", enabled, hovered == 2);
        Palette.drawOutline(g, rects[0].x(), rects[0].y(), rects[2].right(), rects[0].bottom(),
                Palette.DESK_LIP);
    }

    private static void drawArrow(GuiGraphics g, Font font, Rect r, String glyph,
                                  boolean enabled, boolean hot) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), hot && enabled ? Palette.DESK : Palette.ROW);
        g.drawString(font, glyph, r.x() + (r.w() - font.width(glyph)) / 2, r.y() + 3,
                !enabled ? Palette.INK_DIM : hot ? Palette.WARM_GLOW : Palette.BAR_FILL, false);
    }

    // ── Status chip ──

    /**
     * The colours a status reads in. Kept as a triple rather than one hue so a chip carries its own
     * ground: a coloured word on the row background is a state you have to already know to read.
     */
    public enum Chip {
        WORKING(0xFFFFB347, 0xFF7A5320, 0xFF33240F),
        WAITING(0xFFC4A870, 0xFF4A3620, 0xFF2A1E10),
        BLOCKED(0xFFD97A63, 0xFF6E2C1E, 0xFF331611),
        SATISFIED(0xFF8FB86E, 0xFF3A5A28, 0xFF1E2A14),
        PAUSED(0xFF8A7550, 0xFF3B2A17, 0xFF221809);

        public final int ink;
        public final int edge;
        public final int fill;

        Chip(int ink, int edge, int fill) {
            this.ink = ink;
            this.edge = edge;
            this.fill = fill;
        }
    }

    public static final int CHIP_H = 11;

    public static int chipWidth(Font font, String label) {
        return font.width(label) + 8;
    }

    public static void drawChip(GuiGraphics g, Font font, Chip style, String label, int x, int y) {
        int w = chipWidth(font, label);
        g.fill(x, y, x + w, y + CHIP_H, style.fill);
        Palette.drawOutline(g, x, y, x + w, y + CHIP_H, style.edge);
        g.drawString(font, label, x + 4, y + 2, style.ink, false);
    }

    // ── Pill ──

    public static final int PILL_H = 13;

    public static Rect pillLayout(Font font, int x, int y, String label) {
        return new Rect(x, y, font.width(label) + 12, PILL_H);
    }

    public static void drawPill(GuiGraphics g, Font font, Rect r, String label,
                                boolean enabled, boolean hot, int ink) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), hot && enabled ? Palette.DESK : Palette.ROW);
        Palette.drawOutline(g, r.x(), r.y(), r.right(), r.bottom(), Palette.DESK_LIP);
        g.drawString(font, label, r.x() + 6, r.y() + 3,
                !enabled ? Palette.INK_DIM : hot ? Palette.WARM_GLOW : ink, false);
    }

    // ── Row tool ──

    public static final int TOOL = 11;

    /** The small square button that lives at the end of a row: delete, edit, and their kin. */
    public static void drawTool(GuiGraphics g, Font font, Rect r, String glyph,
                                boolean hot, boolean destructive) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), hot ? Palette.DESK : Palette.ROW);
        Palette.drawOutline(g, r.x(), r.y(), r.right(), r.bottom(),
                destructive && hot ? Chip.BLOCKED.edge : Palette.SLOT_EDGE);
        g.drawString(font, glyph, r.x() + (r.w() - font.width(glyph)) / 2, r.y() + 2,
                destructive ? (hot ? Chip.BLOCKED.ink : Palette.LABEL_MID)
                        : hot ? Palette.WARM_GLOW : Palette.LABEL_MID, false);
    }

    // ── Item slot ──

    public static final int SLOT = 18;

    /** The sunken square an item sprite sits in, so a list of items reads as inventory. */
    public static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + SLOT, y + SLOT, Palette.SLOT);
        Palette.drawOutline(g, x, y, x + SLOT, y + SLOT, Palette.SLOT_EDGE);
    }

    // ── Field Post chrome ──
    //
    // Vanilla-button colouring rather than the parchment palette, because that is what the Field
    // Post's tabs and toggles already use and these sit beside its search field.

    public static final int CHROME_IDLE = 0xFF3A3A3A;
    public static final int CHROME_HOVER = 0xFF5A5A5A;
    public static final int CHROME_ON = 0xFF5A8A2A;
    public static final int CHROME_ACCENT = 0xFF88DD44;
    public static final int CHROME_TOP = 0xFF555555;
    public static final int CHROME_BOTTOM = 0xFF222222;

    // ── Margin marks ──
    //
    // The clerk's marks against a ruled edge. Drawn from rectangles, never typed: the vanilla font
    // has no glyph for any of them, so a character would arrive as a missing-glyph box.

    /** The ruled edge itself. Everything after this is drawn against it. */
    public static void drawMargin(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x30000000);
        g.fill(x, y, x + 1, y + h, Palette.WELL_EDGE);
    }

    public static final int MARK = 11;

    private static void markPlate(GuiGraphics g, Rect r, boolean latched, boolean hot, int edge) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(),
                latched ? Palette.WAX_SEAL : hot ? Palette.DESK : Palette.DESK_DEEP);
        Palette.drawOutline(g, r.x(), r.y(), r.right(), r.bottom(),
                latched ? Palette.WAX_RIM : edge);
    }

    /** Hold — two upright bars. Latches wax-red, and is the only red on a row. */
    public static void drawHoldMark(GuiGraphics g, Rect r, boolean held, boolean hot) {
        markPlate(g, r, held, hot, Palette.BRASS_DEEP);
        int ink = held ? Palette.CARD : hot ? Palette.BRASS_HOT : Palette.BRASS;
        g.fill(r.x() + 3, r.y() + 2, r.x() + 5, r.bottom() - 2, ink);
        g.fill(r.x() + 6, r.y() + 2, r.x() + 8, r.bottom() - 2, ink);
    }

    /** Copy — one square lying over another, the only mark that is a picture of its verb. */
    public static void drawCopyMark(GuiGraphics g, Rect r, boolean hot) {
        markPlate(g, r, false, hot, Palette.BRASS_DEEP);
        int ink = hot ? Palette.BRASS_HOT : Palette.BRASS;
        int back = hot ? Palette.DESK : Palette.DESK_DEEP;
        Palette.drawOutline(g, r.x() + 2, r.y() + 2, r.x() + 8, r.y() + 8, ink);
        g.fill(r.x() + 4, r.y() + 4, r.right() - 1, r.bottom() - 1, back);
        Palette.drawOutline(g, r.x() + 4, r.y() + 4, r.right() - 1, r.bottom() - 1, ink);
    }

    /** Strike — one ruled line, the mark a clerk makes through a line that is finished with. */
    public static void drawStrikeMark(GuiGraphics g, Rect r, boolean hot) {
        markPlate(g, r, false, hot, hot ? Chip.BLOCKED.edge : Palette.SLOT_EDGE);
        int mid = r.y() + r.h() / 2;
        g.fill(r.x() + 2, mid, r.right() - 2, mid + 1, hot ? Chip.BLOCKED.ink : Palette.LABEL_MID);
    }

    /** The width a list must leave clear on its right for {@link #drawScrollbar}. */
    public static final int SCROLLBAR_W = 6;

    /**
     * A scrollbar in the vanilla list style, drawn only when there is more than fits.
     *
     * <p>Its absence is information: a list with no bar is a list you are seeing all of. Silently
     * clipping the rest is how a player concludes the kitchen only makes five things.</p>
     */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int h, int first, int shown, int total) {
        if (total <= shown || h <= 0) return;
        g.fill(x, y, x + SCROLLBAR_W, y + h, 0xFF000000);
        int thumb = Math.max(12, h * shown / total);
        int span = h - thumb;
        int travel = Math.max(1, total - shown);
        int top = y + (int) ((long) span * Math.min(first, travel) / travel);
        g.fill(x, top, x + SCROLLBAR_W, top + thumb, 0xFF8B8B8B);
        g.fill(x, top, x + SCROLLBAR_W - 1, top + thumb - 1, 0xFFC6C6C6);
    }

    // ── Progress bar ──

    /** A bordered track with a fill, matched to the row height rather than the career board's. */
    public static void drawBar(GuiGraphics g, int x, int y, int w, float progress, boolean done) {
        g.fill(x, y, x + w, y + 6, Palette.BAR_TRACK);
        Palette.drawOutline(g, x, y, x + w, y + 6, Palette.WELL_EDGE);
        int filled = Math.round((w - 2) * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + 5, done ? Palette.INK_GOOD : Palette.BAR_FILL);
        }
    }
}
