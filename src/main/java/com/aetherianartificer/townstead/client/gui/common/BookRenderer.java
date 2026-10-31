package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * An open bound book lying on a desk, and the type a record set in it uses. The page grid, the
 * rubric headings, the dot-leader rows and the dotted writing lines live here so every page of
 * every book is set the same way.
 */
public final class BookRenderer {
    public static final int COVER = 0xFF6B2A18;
    public static final int COVER_EDGE = 0xFF3A150A;
    public static final int PAGE = 0xFFF4E6C4;
    public static final int PAGE_SHADE = 0xFFE6D2A6;
    public static final int TAB_IDLE = 0xFFD8C193;
    public static final int GUTTER = 0xFF5A3A1E;
    public static final int RULE = 0x337A6540;

    public static final int INK = Palette.CARD_INK;
    public static final int FADED = Palette.CARD_INK_DIM;
    /** Headings and anything that must be read before acting, as a scribe's red. */
    public static final int RUBRIC = 0xFF9E2B1D;
    public static final int LEADER = 0x997A6540;
    /** An editable line under the pointer, and the solid line being written on. */
    public static final int LINE_HOT = Palette.BRASS_DEEP;
    public static final int SELECTED = 0x2E8A5F1E;
    public static final int HOVER = 0x168A5F1E;

    /** The page grid. Every placement on a page is one of these. */
    public static final int MARGIN_X = 16;
    public static final int MARGIN_TOP = 14;
    public static final int MARGIN_FOOT = 12;
    public static final int SECTION = 12;
    public static final int HEAD_GAP = 6;
    public static final int ROW = 14;
    public static final int DETAIL = 10;
    /** Air between a row and its detail line: rows sit six apart, so a detail needs a little too. */
    public static final int DETAIL_GAP = 2;
    /** A heading's text, its hairline and the gap below: where its first row starts. */
    public static final int HEADING = 9 + 2 + HEAD_GAP;
    /** Text sits three pixels into a row so its eight pixel glyphs centre in fourteen. */
    public static final int TEXT_Y = 3;
    public static final int TAB_H = 14;
    private static final int TAB_PAD = 10;
    private static final int COVER_MARGIN = 5;

    private BookRenderer() {}

    /** The two page areas of a book drawn at this rectangle, left then right. */
    public static Controls.Rect[] pages(Controls.Rect book) {
        int inner = book.w() - COVER_MARGIN * 2;
        int half = inner / 2;
        int y = book.y() + COVER_MARGIN, h = book.h() - COVER_MARGIN * 2;
        return new Controls.Rect[]{
                new Controls.Rect(book.x() + COVER_MARGIN, y, half - 1, h),
                new Controls.Rect(book.x() + COVER_MARGIN + half + 1, y, inner - half - 1, h)};
    }

    /** Where a page's text column starts and how wide it runs. */
    public static int left(Controls.Rect page) { return page.x() + MARGIN_X; }
    public static int width(Controls.Rect page) { return page.w() - 2 * MARGIN_X; }
    /** The first line under the running head. */
    public static int top(Controls.Rect page) { return page.y() + MARGIN_TOP + 9 + SECTION; }
    /** The foot line's top: the last line a page can carry. */
    public static int foot(Controls.Rect page) { return page.bottom() - MARGIN_FOOT - 9; }

    public static void draw(GuiGraphics g, Controls.Rect book) {
        g.fill(book.x() + 2, book.y() + 3, book.right() + 2, book.bottom() + 3, 0x66000000);
        g.fill(book.x(), book.y(), book.right(), book.bottom(), COVER);
        Palette.drawOutline(g, book.x(), book.y(), book.right(), book.bottom(), COVER_EDGE);
        Controls.Rect[] pages = pages(book);
        for (int i = 0; i < 2; i++) {
            Controls.Rect page = pages[i];
            g.fill(page.x(), page.y(), page.right(), page.bottom(), PAGE);
            // A soft darkening toward the gutter, as the page curves into the binding.
            for (int step = 0; step < 6; step++) {
                int alpha = 0x18 - step * 3;
                int x = i == 0 ? page.right() - 1 - step * 2 : page.x() + step * 2;
                g.fill(x, page.y(), x + 2, page.bottom(), (alpha << 24) | 0x5A3A1E);
            }
            g.fill(page.x(), page.bottom() - 1, page.right(), page.bottom(), PAGE_SHADE);
        }
        int gutter = pages[0].right();
        g.fill(gutter, pages[0].y(), gutter + 2, pages[0].bottom(), GUTTER);
    }

    public static int tabWidth(Font font, String label) {
        return font.width(label) + 2 * TAB_PAD;
    }

    /**
     * A folder tab standing on the book's top edge. An idle tab stops at the cover; the open one is
     * cut from the page itself and runs down into it, so there is no line between tab and page.
     * Draw idle tabs before the book and the open tab after it.
     */
    public static void tab(GuiGraphics g, Font font, Controls.Rect tab, int pageTop, String label, boolean open, boolean hot) {
        int bottom = open ? pageTop : tab.bottom();
        g.fill(tab.x(), tab.y(), tab.right(), bottom, open ? PAGE : TAB_IDLE);
        g.fill(tab.x(), tab.y(), tab.right(), tab.y() + 1, COVER_EDGE);
        g.fill(tab.x(), tab.y(), tab.x() + 1, bottom, COVER_EDGE);
        g.fill(tab.right() - 1, tab.y(), tab.right(), bottom, COVER_EDGE);
        if (hot && !open) g.fill(tab.x() + 1, tab.y() + 1, tab.right() - 1, tab.y() + 2, LINE_HOT);
        g.drawString(font, label, tab.x() + TAB_PAD, tab.y() + 4, open ? INK : FADED, false);
    }

    /** The running head: small faded text at the top of a page, left or right aligned. */
    public static void runningHead(GuiGraphics g, Font font, Controls.Rect page, String text, boolean right) {
        String shown = fit(font, text, width(page));
        int x = right ? left(page) + width(page) - font.width(shown) : left(page);
        g.drawString(font, shown, x, page.y() + MARGIN_TOP, FADED, false);
    }

    /** A rubric heading with its hairline; returns where its first row starts. */
    public static int heading(GuiGraphics g, Font font, String text, int x, int y, int w) {
        g.drawString(font, fit(font, text, w), x, y, RUBRIC, false);
        g.fill(x, y + 10, x + w, y + 11, 0x559E2B1D);
        return y + HEADING;
    }

    /**
     * {@code Label ........ Value}. The value keeps its full width when it can; the label gives way
     * first, since the value is the reason for the row.
     */
    public static void leader(GuiGraphics g, Font font, String label, String value, int x, int y, int w,
                              int labelColor, int valueColor) {
        int valueRoom = Math.max(w / 2, w - font.width(label) - 12);
        String shownValue = fit(font, value, valueRoom);
        int valueX = x + w - font.width(shownValue);
        String shownLabel = fit(font, label, Math.max(10, valueX - x - 8));
        g.drawString(font, shownLabel, x, y + TEXT_Y, labelColor, false);
        g.drawString(font, shownValue, valueX, y + TEXT_Y, valueColor, false);
        dots(g, x + font.width(shownLabel) + 4, valueX - 4, y + TEXT_Y + 7);
    }

    /** Dot leaders between two x positions, on a baseline. Dots fall on a 3px grid so rows line up. */
    public static void dots(GuiGraphics g, int from, int to, int baseline) {
        for (int x = from + Math.floorMod(-from, 3); x < to; x += 3) g.fill(x, baseline, x + 1, baseline + 1, LEADER);
    }

    /** A writing line: dotted while idle, solid brass while hovered or written on. */
    public static void line(GuiGraphics g, int x, int y, int w, boolean hot, boolean solid) {
        if (solid) { g.fill(x, y, x + w, y + 1, LINE_HOT); return; }
        int color = hot ? LINE_HOT : LEADER;
        for (int i = 0; i < w; i += 2) g.fill(x + i, y, x + i + 1, y + 1, color);
    }

    /** A selectable row's ground: a pale wash, and a brass edge once chosen. */
    public static void rowGround(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hot) {
        if (!selected && !hot) return;
        g.fill(x - 4, y, x + w + 4, y + h, selected ? SELECTED : HOVER);
        if (selected) g.fill(x - 4, y, x - 2, y + h, LINE_HOT);
    }

    /** Wrapped text, at most {@code maxLines}; returns the y below the last line drawn. */
    public static int wrap(GuiGraphics g, Font font, net.minecraft.network.chat.Component text, int x, int y, int w,
                           int color, int maxLines, boolean centred) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(20, w));
        int shown = Math.min(maxLines, lines.size());
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = lines.get(i);
            int lx = centred ? x + (w - font.width(line)) / 2 : x;
            g.drawString(font, line, lx, y, color, false);
            y += DETAIL;
        }
        return y;
    }

    public static int lines(Font font, net.minecraft.network.chat.Component text, int w, int maxLines) {
        if (text.getString().isBlank()) return 0;
        return Math.min(maxLines, font.split(text, Math.max(20, w)).size());
    }

    public static String fit(Font font, String text, int w) {
        if (font.width(text) <= w) return text;
        return font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
    }

    /** A thin rule between rows on a page. */
    public static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, RULE);
    }
}
