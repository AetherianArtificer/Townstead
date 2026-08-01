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

    // ── The button ──
    //
    // Vanilla's flat plate, as the Field Post already draws its tabs and mode buttons: a grey body
    // with a lit top rule and a shadowed bottom one, inside a black border, going green when it is
    // the chosen one. Every control below is made of this, so they read as one kit rather than as
    // a parchment picker sitting next to a Minecraft button.

    public static final int CHROME_IDLE = 0xFF3A3A3A;
    public static final int CHROME_HOVER = 0xFF5A5A5A;
    public static final int CHROME_ON = 0xFF5A8A2A;
    public static final int CHROME_ON_HOVER = 0xFF6EA436;
    public static final int CHROME_ACCENT = 0xFF88DD44;
    public static final int CHROME_TOP = 0xFF555555;
    public static final int CHROME_BOTTOM = 0xFF222222;
    public static final int CHROME_OFF = 0xFF2A2A2A;

    private static final int INK_ON = 0xFFFFFFFF;
    private static final int INK_IDLE = 0xFFCCCCCC;
    private static final int INK_HOT = 0xFFFFFFA0;
    private static final int INK_OFF = 0xFF6A6A6A;

    /** The plate only, for a button whose face is drawn rather than written. */
    public static void drawButtonPlate(GuiGraphics g, Rect r, boolean on, boolean hot, boolean enabled) {
        int body = !enabled ? CHROME_OFF
                : on ? (hot ? CHROME_ON_HOVER : CHROME_ON)
                : hot ? CHROME_HOVER : CHROME_IDLE;
        Palette.drawOutline(g, r.x(), r.y(), r.right(), r.bottom(), 0xFF000000);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, body);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, on ? CHROME_ACCENT : CHROME_TOP);
        g.fill(r.x() + 1, r.bottom() - 2, r.right() - 1, r.bottom() - 1, CHROME_BOTTOM);
    }

    /** The ink a button's label takes for a given state. */
    public static int buttonInk(boolean on, boolean hot, boolean enabled) {
        return !enabled ? INK_OFF : on ? INK_ON : hot ? INK_HOT : INK_IDLE;
    }

    /** A vanilla plate with a centred label on it. */
    public static void drawButton(GuiGraphics g, Font font, Rect r, String label,
                                  boolean on, boolean hot, boolean enabled) {
        drawButtonPlate(g, r, on, hot, enabled);
        int room = r.w() - 6;
        String shown = label;
        if (font.width(shown) > room) {
            while (!shown.isEmpty() && font.width(shown + "..") > room) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown += "..";
        }
        g.drawString(font, shown, r.x() + (r.w() - font.width(shown)) / 2,
                r.y() + (r.h() - font.lineHeight) / 2 + 1, buttonInk(on, hot, enabled), false);
    }

    // ── Segmented picker ──

    private static final int SEG_PAD = 6;
    public static final int SEG_H = 14;

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
     * A row of vanilla buttons acting as one picker, exactly as the Field Post draws Crops | Soil.
     * The chosen one is green with a lit top edge; a disabled picker greys out entirely.
     */
    public static void drawSegments(GuiGraphics g, Font font, Rect[] rects, String[] labels,
                                    int selected, int hovered) {
        boolean enabled = selected >= 0;
        for (int i = 0; i < rects.length; i++) {
            drawButton(g, font, rects[i], labels[i], i == selected, i == hovered, enabled);
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

    /** The labels of a stepper's five parts, in order. Index 2 is the value plate, not a button. */
    private static final String[] STEP_LABELS = {"-10", "-", "", "+", "+10"};

    /** How much each part of a stepper moves the number. The plate moves nothing. */
    public static final int[] STEP_AMOUNTS = {-10, -1, 0, 1, 10};

    /** The number of parts a stepper has, so callers never have to guess. */
    public static final int STEPPER_PARTS = 5;

    /**
     * {@code [−10][−][ value ][+][+10]}, laid out around a plate wide enough for the number.
     *
     * <p>Five parts rather than three because the coarse step used to be hidden behind holding
     * shift, which nobody discovers. Callers index them with {@link #STEP_AMOUNTS}.</p>
     */
    public static Rect[] stepperLayout(Font font, int x, int y, String value) {
        int coarse = font.width("-10") + 4;
        int fine = 11;
        int plate = Math.max(20, font.width(value) + 8);
        int[] widths = {coarse, fine, plate, fine, coarse};
        Rect[] out = new Rect[STEPPER_PARTS];
        int cursor = x;
        for (int i = 0; i < STEPPER_PARTS; i++) {
            out[i] = new Rect(cursor, y, widths[i], SEG_H);
            cursor += widths[i];
        }
        return out;
    }

    /** Four vanilla buttons around a sunken value plate, so the number reads as a readout. */
    public static void drawStepper(GuiGraphics g, Font font, Rect[] rects, String value,
                                   boolean enabled, int hovered) {
        for (int i = 0; i < rects.length; i++) {
            if (i == 2) continue;
            drawButton(g, font, rects[i], STEP_LABELS[i], false, hovered == i, enabled);
        }
        Rect plate = rects[2];
        Palette.drawOutline(g, plate.x(), plate.y(), plate.right(), plate.bottom(), 0xFF000000);
        g.fill(plate.x() + 1, plate.y() + 1, plate.right() - 1, plate.bottom() - 1,
                enabled ? 0xFF101010 : 0xFF1E1E1E);
        g.drawString(font, value, plate.x() + (plate.w() - font.width(value)) / 2,
                plate.y() + (plate.h() - font.lineHeight) / 2 + 1,
                enabled ? 0xFFFFFFFF : INK_OFF, false);
    }

    /** Total width of a stepper for this value, so a caller can reserve room for it. */
    public static int stepperWidth(Font font, String value) {
        Rect[] r = stepperLayout(font, 0, 0, value);
        return r[r.length - 1].right();
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

    /** A lone vanilla button. The ink argument is kept for callers that want a warning colour. */
    public static void drawPill(GuiGraphics g, Font font, Rect r, String label,
                                boolean enabled, boolean hot, int ink) {
        drawButton(g, font, r, label, false, hot, enabled);
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

    /**
     * A titled sub-panel inside a window: a faint recess with its heading sitting on the rule.
     *
     * <p>What groups a form into "how much" and "who may work it" rather than a column of labels
     * that all look equally important.</p>
     */
    public static void drawBox(GuiGraphics g, Font font, Rect r, String title) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x40000000);
        if (title == null || title.isEmpty()) {
            Palette.drawOutline(g, r.x(), r.y(), r.right(), r.bottom(), Palette.DESK_LIP);
            return;
        }
        String label = title.toUpperCase(java.util.Locale.ROOT);
        int w = font.width(label);
        // The top rule is drawn in two runs around the heading rather than punched out after:
        // filling with a zero-alpha colour draws nothing, so the "punch" left the rule striking
        // straight through the text.
        g.fill(r.x(), r.y(), r.x() + 5, r.y() + 1, Palette.DESK_LIP);
        g.fill(Math.min(r.x() + 9 + w, r.right()), r.y(), r.right(), r.y() + 1, Palette.DESK_LIP);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), Palette.DESK_LIP);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), Palette.DESK_LIP);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), Palette.DESK_LIP);
        g.drawString(font, label, r.x() + 7, r.y() - 3, Palette.LABEL_MID, false);
    }

    // ── Margin marks ──
    //
    // The clerk's marks against a ruled edge. Drawn from rectangles, never typed: the vanilla font
    // has no glyph for any of them, so a character would arrive as a missing-glyph box.

    /** The ruled edge itself. Everything after this is drawn against it. */
    public static void drawMargin(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x30000000);
        g.fill(x, y, x + 1, y + h, Palette.WELL_EDGE);
    }

    public static final int MARK = 14;

    /**
     * A mark sits on the same vanilla plate as every other button, so the margin reads as controls
     * rather than as decoration. Only Hold latches, and it latches green like a chosen tab.
     */
    private static void markPlate(GuiGraphics g, Rect r, boolean latched, boolean hot, int edge) {
        drawButtonPlate(g, r, latched, hot, true);
    }

    /** Hold — two upright bars. Latches wax-red, and is the only red on a row. */
    public static void drawHoldMark(GuiGraphics g, Rect r, boolean held, boolean hot) {
        markPlate(g, r, held, hot, Palette.BRASS_DEEP);
        int ink = buttonInk(held, hot, true);
        g.fill(r.x() + 4, r.y() + 3, r.x() + 6, r.bottom() - 3, ink);
        g.fill(r.x() + 8, r.y() + 3, r.x() + 10, r.bottom() - 3, ink);
    }

    /** Copy — one square lying over another, the only mark that is a picture of its verb. */
    public static void drawCopyMark(GuiGraphics g, Rect r, boolean hot) {
        markPlate(g, r, false, hot, Palette.BRASS_DEEP);
        int ink = buttonInk(false, hot, true);
        int back = hot ? CHROME_HOVER : CHROME_IDLE;
        Palette.drawOutline(g, r.x() + 3, r.y() + 3, r.x() + 10, r.y() + 10, ink);
        g.fill(r.x() + 5, r.y() + 5, r.right() - 2, r.bottom() - 2, back);
        Palette.drawOutline(g, r.x() + 5, r.y() + 5, r.right() - 2, r.bottom() - 2, ink);
    }

    /** Strike — one ruled line, the mark a clerk makes through a line that is finished with. */
    public static void drawStrikeMark(GuiGraphics g, Rect r, boolean hot) {
        markPlate(g, r, false, hot, hot ? Chip.BLOCKED.edge : Palette.SLOT_EDGE);
        int mid = r.y() + r.h() / 2;
        g.fill(r.x() + 3, mid, r.right() - 3, mid + 1, hot ? Chip.BLOCKED.ink : buttonInk(false, false, true));
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
