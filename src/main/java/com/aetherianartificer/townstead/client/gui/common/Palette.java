package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The ink-on-parchment palette every Townstead screen draws from, plus the colour arithmetic that
 * derives states from it.
 *
 * <p>Lives in {@code gui.common} for the same reason {@link FrameRenderer} does: the Career board
 * and the Field Post grid had each grown their own private set of browns, so a tone tuned on one
 * screen never reached the other. A value here is the family's value.</p>
 *
 * <p>Names describe the ROLE, not the hue, so a future retune changes the constant and not every
 * call site. Seven declaration-only constants (PLAQUE_*, ROPE_LIT, ARM_INK, GOLD) were dropped
 * rather than carried across, since a palette nobody draws from is just a list.</p>
 */
public final class Palette {
    private Palette() {}

    // Ink, darkest to lightest. INK_DIM is DARKER than INK_TEXT on purpose: the board sits around
    // 0x9C8867, so a pale "dim" lands on the background's own value and disappears.
    public static final int INK_HEADER = 0xFF1F1305;
    public static final int INK_TEXT = 0xFF3A2410;
    public static final int INK_DIM = 0xFF6E5430;
    public static final int INK_ACCENT = 0xFF8A5A12;

    /** Semantic ink. Separate from the accent so "good" never doubles as "emphasis". */
    public static final int INK_GOOD = 0xFF2E5E1E;
    public static final int INK_BAD = 0xFF8C2E1A;

    /** Pale paper, and what is written on it. */
    public static final int CARD = 0xFFF3E7C8;
    public static final int CARD_INK = 0xFF3A2A14;
    public static final int CARD_INK_DIM = 0xFF7A6540;

    /** Text laid over dark furniture rather than paper. */
    public static final int LABEL_LIGHT = 0xFFEBD9AE;
    public static final int LABEL_DIM = 0xFF9C8054;

    public static final int BAR_TRACK = 0xFFB8985C;
    public static final int BAR_FILL = 0xFFFFB347;

    /** Ruled ink: an unwalked prerequisite line. */
    public static final int ROPE = 0xFF5A4426;
    public static final int WARM_GLOW = 0xFFFFE680;

    // ── The desk ───────────────────────────────────────────────────────────
    //
    // The board's ground, and the reason the screen stopped being one flat sandy field. Everything
    // on it used to sit within a few percent of the parchment behind it, so no boundary carried any
    // value contrast and every edge had to be drawn as an outline. These are DARK on purpose: the
    // cards are the light objects, the desk is what they lie on.

    public static final int DESK = 0xFF3E2C19;
    public static final int DESK_DEEP = 0xFF241A0E;
    public static final int DESK_EDGE = 0xFF1A1208;
    public static final int DESK_LIP = 0xFF5A4128;

    /** An alcove's recessed interior before its own path tint is washed over it. */
    public static final int ALCOVE = 0xFF2A2013;

    // ── Wells and rows ─────────────────────────────────────────────────────
    //
    // A "well" is a pane sunk into the desk: list columns, detail strips, anything that holds
    // rows. Rows sit one step lighter inside it, and the selected row one step lighter again, so
    // the three depths are readable without an outline on every edge.

    public static final int WELL = 0xFF1E150B;
    public static final int WELL_EDGE = 0xFF3B2A17;
    public static final int ROW = 0xFF2A1E10;

    /** An item slot's sunken square, as it reads in an inventory. */
    public static final int SLOT = 0xFF150E06;
    public static final int SLOT_EDGE = 0xFF4A3620;

    /** Between LABEL_LIGHT and LABEL_DIM: a control's resting text, still clearly readable. */
    public static final int LABEL_MID = 0xFFA08A62;

    /** An explanation under a row. Warmer than LABEL_DIM because it is meant to be read. */
    public static final int LABEL_WARM = 0xFFC4A870;

    // Brass does the contrast work the palette flip would otherwise have needed. Acquired marks and
    // walked routes are lit metal; everything unreached is cold and stays out of the way.
    public static final int BRASS = 0xFFE8B455;
    public static final int BRASS_DEEP = 0xFF8A5F1E;
    public static final int BRASS_HOT = 0xFFFFF0C8;
    public static final int COLD = 0xFF4A4034;
    public static final int COLD_DEEP = 0xFF3A322A;
    public static final int HOVER_OUTLINE = 0xB0523A18;
    public static final int SELECT_OUTLINE = 0xFF2E1F0C;
    public static final int WAX_SEAL = 0xFFA02020;
    public static final int WAX_RIM = 0xFF701010;

    // Index leaves: the active tab is the pale edge of the leaf you opened, the rest are shadowed
    // edges behind it. Bordered in ink rather than frame shadow, which on paper read as a scorch.
    public static final int TAB_ACTIVE = 0xFFE9D7AD;
    public static final int TAB_IDLE = 0xFF49321B;
    public static final int TAB_HOVER = 0xFF72502B;
    public static final int TAB_BORDER = 0xFF24160B;
    public static final int NAV_INK = 0xFF3E2510;

    /** Scales a colour toward black, keeping alpha, so unfocused work recedes instead of vanishing. */
    public static int dim(int argb, float factor) {
        int a = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * factor);
        int g = Math.round(((argb >> 8) & 0xFF) * factor);
        int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Scales alpha only, for ink that should thin out rather than change colour. */
    public static int fade(int argb, float factor) {
        int a = Math.round(((argb >>> 24) == 0 ? 255 : (argb >>> 24)) * factor);
        return (Mth.clamp(a, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    /** A one-pixel rectangle outline, exclusive of x1/y1 like {@link GuiGraphics#fill}. */
    public static void drawOutline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** A filled progress bar over a sunken track. */
    public static void drawBar(GuiGraphics g, int x, int y, int barWidth, float progress, int color) {
        g.fill(x, y, x + barWidth, y + 3, 0x503A2410);
        g.fill(x, y, x + Math.round(barWidth * Mth.clamp(progress, 0f, 1f)), y + 3,
                0xFF000000 | (color & 0xFFFFFF));
    }
}
