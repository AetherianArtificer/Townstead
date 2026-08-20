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
    // Vanilla's own button art, nine-sliced to whatever size a control needs. Imitating the plate
    // with flat fills always read as almost-Minecraft; using the real texture ends the argument.
    // A chosen button is the same art tinted green, so selection still reads at a glance.

    /** Kept for the marks that fill their glyph's backing on top of the plate. */
    public static final int CHROME_IDLE = 0xFF6D6D6D;
    public static final int CHROME_HOVER = 0xFF7E7E9E;

    //? if >=1.21 {
    private static final net.minecraft.resources.ResourceLocation BUTTON_SPRITE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button");
    private static final net.minecraft.resources.ResourceLocation BUTTON_SPRITE_HOT =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final net.minecraft.resources.ResourceLocation BUTTON_SPRITE_OFF =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button_disabled");
    //?}

    /** The plate only, for a button whose face is drawn rather than written. */
    public static void drawButtonPlate(GuiGraphics g, Rect r, boolean on, boolean hot, boolean enabled) {
        if (on && enabled) g.setColor(0.60f, 0.95f, 0.35f, 1f);
        //? if >=1.21 {
        g.blitSprite(!enabled ? BUTTON_SPRITE_OFF : hot ? BUTTON_SPRITE_HOT : BUTTON_SPRITE,
                r.x(), r.y(), r.w(), r.h());
        //?} else {
        /*int state = !enabled ? 0 : hot ? 2 : 1;
        g.blitNineSliced(net.minecraft.client.gui.components.AbstractWidget.WIDGETS_LOCATION,
                r.x(), r.y(), r.w(), r.h(), 20, 4, 200, 20, 0, 46 + state * 20);
        *///?}
        if (on && enabled) g.setColor(1f, 1f, 1f, 1f);
    }

    /** The ink a button's label takes: vanilla's white, or vanilla's disabled grey. */
    public static int buttonInk(boolean on, boolean hot, boolean enabled) {
        return enabled ? 0xFFFFFFFF : 0xFFA0A0A0;
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
                r.y() + (r.h() - font.lineHeight) / 2 + 1, buttonInk(on, hot, enabled), true);
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

    // ── Palette tab bar ──
    //
    // The Field Post's Crops | Soil toggle, lifted verbatim so every palette splits its shelves
    // the same way: equal flat tabs spanning the full palette width directly below the search
    // field, with the warm separator rule beneath. Flat fills rather than the vanilla button art
    // on purpose — the bar is furniture of the panel, not a control floating on it.

    public static final int TAB_H = 14;
    /** What the bar adds below the search field: gap, tabs, gap, separator, gap. */
    public static final int TAB_BAR_H = 4 + TAB_H + 2 + 2;

    /** Equal-width tabs spanning {@code w}; the last one absorbs the rounding remainder. */
    public static Rect[] tabLayout(int x, int y, int w, int count) {
        Rect[] out = new Rect[count];
        int each = w / count;
        for (int i = 0; i < count; i++) {
            out[i] = new Rect(x + each * i, y, i == count - 1 ? w - each * i : each, TAB_H);
        }
        return out;
    }

    public static void drawTabs(GuiGraphics g, Font font, Rect[] rects, String[] labels,
                                int selected, int hovered) {
        for (int i = 0; i < rects.length; i++) {
            Rect r = rects[i];
            boolean active = i == selected;
            boolean hot = i == hovered;
            g.fill(r.x(), r.y(), r.right(), r.bottom(),
                    active ? 0xFF5A8A2A : hot ? 0xFF5A5A5A : 0xFF3A3A3A);
            g.fill(r.x(), r.y(), r.right(), r.y() + 1, active ? 0xFF88DD44 : 0xFF555555);
            g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), 0xFF222222);
            g.drawCenteredString(font, labels[i], r.x() + r.w() / 2, r.y() + 3,
                    active ? 0xFFFFFFFF : hot ? 0xFFDDDDDD : 0xFFAAAAAA);
        }
        Rect first = rects[0];
        g.fill(first.x(), first.y() + TAB_H + 1, rects[rects.length - 1].right(),
                first.y() + TAB_H + 2, 0x40FFDEA0);
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
        // Each part is sized from its own label plus more padding than drawButton's trim
        // threshold. The old fixed widths sat under it, so every "-10" rendered as "-1..".
        int plate = Math.max(20, font.width(value) + 8);
        Rect[] out = new Rect[STEPPER_PARTS];
        int cursor = x;
        for (int i = 0; i < STEPPER_PARTS; i++) {
            int w = i == 2 ? plate : font.width(STEP_LABELS[i]) + 8;
            out[i] = new Rect(cursor, y, w, SEG_H);
            cursor += w;
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
                enabled ? 0xFFFFFFFF : 0xFFA0A0A0, false);
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

    /** Matches {@link #SEG_H} and {@link #MARK}, so a chip centres on the same band as buttons. */
    public static final int CHIP_H = 14;

    public static int chipWidth(Font font, String label) {
        return font.width(label) + 8;
    }

    public static void drawChip(GuiGraphics g, Font font, Chip style, String label, int x, int y) {
        int w = chipWidth(font, label);
        g.fill(x, y, x + w, y + CHIP_H, style.fill);
        Palette.drawOutline(g, x, y, x + w, y + CHIP_H, style.edge);
        g.drawString(font, label, x + 4, y + 3, style.ink, false);
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

    /**
     * Where a press in the bar's lane puts the list, or -1 when the press is elsewhere. The lane
     * is forgiving by a couple of pixels each side: a six-pixel target is a test of aim, not a
     * control.
     */
    public static int scrollbarPick(int x, int y, int h, double mx, double my, int shown, int total) {
        if (total <= shown || h <= 0) return -1;
        if (mx < x - 2 || mx >= x + SCROLLBAR_W + 2 || my < y || my >= y + h) return -1;
        return scrollbarDrag(y, h, my, shown, total);
    }

    /**
     * The same mapping with no lane test, for a drag that already owns the bar: the thumb's
     * centre follows the cursor. The inverse of {@link #drawScrollbar}'s own placement, so the
     * thumb lands under the finger rather than jumping past it.
     */
    public static int scrollbarDrag(int y, int h, double my, int shown, int total) {
        if (total <= shown || h <= 0) return 0;
        int thumb = Math.max(12, h * shown / total);
        int span = Math.max(1, h - thumb);
        int travel = total - shown;
        int picked = (int) Math.round((my - y - thumb / 2.0) * travel / span);
        return Math.max(0, Math.min(travel, picked));
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
