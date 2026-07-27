package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.common.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The record's own vocabulary: notched cards, meters, tokens and ticks.
 *
 * <p>Kept apart from {@link RecordPage} so the panel's layout code reads as layout. Every primitive
 * here is drawn from its top-left corner and returns nothing, so a caller composes by advancing a
 * cursor rather than by negotiating with the widget.</p>
 */
final class RecordArt {
    private RecordArt() {}

    static final int PAGE = 0xFFEFE1BE;
    static final int PAGE_HI = 0xFFFCF4DE;
    static final int INK = 0xFF2A1C0C;
    static final int INK_MID = 0xFF5A452A;
    static final int INK_DIM = 0xFF8A7654;
    static final int INK_FAINT = 0xFFB0A084;
    static final int ACCENT = 0xFF8A5F1E;
    static final int GOOD = 0xFF4A7A2E;
    static final int BAD = 0xFFA8322A;
    static final int BAR_BG = 0xFFD6C49A;
    static final int BAR = 0xFFC9902B;
    static final int CARD = 0xFFF6EDD5;
    static final int CARD_EDGE = 0xFFD4C09A;
    static final int STRIP = 0xFFD6BC8A;
    static final int HEAD_WASH = 0x8CD4BA8A;

    /**
     * A card with a single-tone header strip.
     *
     * <p>Square corners. They were briefly notched, on the theory that clipped corners read as
     * tickets pasted into a register, but at this size the notch mostly eats the four pixels a
     * 220px column can least afford and leaves the edge looking chewed rather than cut.</p>
     *
     * <p>The strip tone is the same for every section on purpose. An earlier pass gave each block
     * its own hue, which spent the palette on section identity nobody can decode; the label already
     * says which block this is, so colour is free to mean something. It means state, and appears
     * only on the accent bar and the header's right-hand value.</p>
     */
    static void card(GuiGraphics g, Font font, int x, int y, int w, int h,
                     String label, String value, int accent) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x385A452A);
        g.fill(x, y, x + w, y + h, CARD);

        // The strip is two pixels taller than its own text was leaving room for. At eleven the label
        // sat three pixels off the top and two off the bottom, which is a header wearing its content
        // as a hat rather than holding it.
        int stripH = 13;
        g.fill(x, y, x + w, y + stripH, STRIP);
        g.fill(x, y, x + w, y + 1, 0x8CFFFFFF);
        g.fill(x, y + stripH, x + w, y + stripH + 1, 0x33000000);

        g.fill(x, y, x + 1, y + h, accent);
        g.fill(x + w - 1, y, x + w, y + h, CARD_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, CARD_EDGE);

        g.drawString(font, label, x + 6, y + 3, 0xFF3A2A14, false);
        if (!value.isEmpty()) {
            g.drawString(font, value, x + w - 6 - font.width(value), y + 3, 0xFF5A452A, false);
        }
    }

    /** The header strip's own height including its shadow, so callers place the first row under it. */
    static int stripHeight() { return 15; }

    /**
     * A progress meter with quarter ticks and an end marker.
     *
     * <p>A bare fill cannot tell you whether you are near a threshold that matters, which is the
     * only question anyone asks of a progress bar on this screen.</p>
     */
    static void meter(GuiGraphics g, int x, int y, int w, float frac, boolean met) {
        g.fill(x, y, x + w, y + 4, BAR_BG);
        int fill = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (fill > 0) g.fill(x, y, x + fill, y + 4, met ? GOOD : BAR);
        g.fill(x, y, x + w, y + 1, 0x59FFFFFF);
        for (int t = 1; t < 4; t++) {
            int tx = x + Math.round(w * t / 4f);
            g.fill(tx, y, tx + 1, y + 4, 0x475A452A);
        }
        g.fill(x + w - 1, y - 2, x + w, y + 6, met ? GOOD : 0xFF8A7654);
    }

    /** A skill point. Solid when you can spend it, a hollow ring when you are saving for it. */
    static void token(GuiGraphics g, int x, int y, boolean filled) {
        if (filled) {
            g.fill(x + 1, y, x + 5, y + 6, BAR);
            g.fill(x, y + 1, x + 6, y + 5, BAR);
            g.fill(x + 2, y + 1, x + 4, y + 3, 0xFFF0D089);
        } else {
            g.fill(x + 1, y, x + 5, y + 1, 0xFFA89572);
            g.fill(x + 1, y + 5, x + 5, y + 6, 0xFFA89572);
            g.fill(x, y + 1, x + 1, y + 5, 0xFFA89572);
            g.fill(x + 5, y + 1, x + 6, y + 5, 0xFFA89572);
        }
    }

    /** Cost as a row of tokens, right-aligned to {@code right}. */
    static void tokens(GuiGraphics g, int right, int y, int count, boolean filled) {
        for (int i = 0; i < Math.max(1, count); i++) {
            token(g, right - 6 - i * 8, y, filled && i < count);
        }
    }

    static void tick(GuiGraphics g, int x, int y, boolean ok) {
        int c = ok ? GOOD : BAD;
        if (ok) {
            g.fill(x, y + 3, x + 2, y + 5, c);
            g.fill(x + 1, y + 4, x + 3, y + 6, c);
            g.fill(x + 2, y + 2, x + 4, y + 4, c);
            g.fill(x + 3, y, x + 5, y + 2, c);
        } else {
            for (int i = 0; i < 4; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
                g.fill(x + 3 - i, y + i, x + 4 - i, y + i + 1, c);
            }
        }
    }

    /** Effect kinds: a plus for a number, a star for a grant, a bar for a loss. */
    static void glyph(GuiGraphics g, int x, int y, char kind, int color) {
        switch (kind) {
            case '+' -> {
                g.fill(x + 2, y, x + 3, y + 5, color);
                g.fill(x, y + 2, x + 5, y + 3, color);
            }
            case '*' -> {
                g.fill(x + 2, y, x + 3, y + 5, color);
                g.fill(x, y + 2, x + 5, y + 3, color);
                g.fill(x + 1, y + 1, x + 2, y + 2, color);
                g.fill(x + 3, y + 3, x + 4, y + 4, color);
                g.fill(x + 3, y + 1, x + 4, y + 2, color);
                g.fill(x + 1, y + 3, x + 2, y + 4, color);
            }
            default -> {
                g.fill(x, y, x + 5, y + 1, color);
                g.fill(x, y + 4, x + 5, y + 5, color);
            }
        }
    }

    /** A jump affordance: this row goes somewhere. */
    static void chevron(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color);
        g.fill(x, y + 4, x + 1, y + 5, color);
    }

    /**
     * The rank ladder: named rungs, lit to where you are, with an optional caret under the rung a
     * selected mark needs.
     *
     * <p>Pips tell you how many. Rungs tell you where you are going, which is the question a career
     * screen exists to answer.</p>
     */
    static void ladder(GuiGraphics g, Font font, int x, int y, int w, int rungs, int reached,
                       int marked, int markColor, java.util.List<String> names) {
        if (rungs <= 0) return;
        int step = w / rungs;
        for (int i = 0; i < rungs; i++) {
            int rx = x + i * step;
            boolean lit = i < reached;
            g.fill(rx, y, rx + step - 2, y + 5, lit ? BAR : 0xFFD2C09A);
            g.fill(rx, y, rx + step - 2, y + 1, lit ? 0xFFE8C877 : 0xFFDCCCA8);
            if (names != null && i < names.size()) {
                String label = abbreviate(font, names.get(i), step - 3);
                g.drawString(font, label, rx, y + 7, lit ? INK_MID : INK_FAINT, false);
            }
            if (marked == i + 1) {
                int cx = rx + (step - 2) / 2;
                g.fill(cx - 2, y + 6, cx + 3, y + 7, markColor);
                g.fill(cx - 1, y + 7, cx + 2, y + 8, markColor);
                g.fill(cx, y + 8, cx + 1, y + 9, markColor);
            }
        }
    }

    /** Trims a rank name to the width a rung can actually hold, without an ellipsis at this size. */
    static String abbreviate(Font font, String text, int room) {
        String cut = text;
        while (cut.length() > 1 && font.width(cut) > room) cut = cut.substring(0, cut.length() - 1);
        return cut;
    }

    /** One entry on the chronicle's spine: a wax dot beside the rule. */
    static void waxDot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 5, y + 5, BAD);
        g.fill(x + 1, y + 1, x + 4, y + 4, 0xFFC8564A);
        g.fill(x + 1, y + 1, x + 3, y + 2, 0xFFD87A6A);
    }
}
