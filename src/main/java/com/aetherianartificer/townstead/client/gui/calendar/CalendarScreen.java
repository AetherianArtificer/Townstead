package com.aetherianartificer.townstead.client.gui.calendar;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stardew-style month-grid calendar. Reads the active profile shape from
 * {@link CalendarClientStore} (months list, days_per_week, weekdays, year
 * suffix / eras, epoch) and renders any month/year locally.
 *
 * <p>Custom-drawn UI: nav arrows, Today button, and day cells are not
 * vanilla {@code Button} widgets — they're drawn directly so they match the
 * parchment palette. Click handling lives in {@link #mouseClicked} via
 * hit-tested rectangles tracked per render.</p>
 */
public class CalendarScreen extends Screen {

    // ── Layout ─────────────────────────────────────────────────────────────
    private static final int FRAME_THICKNESS = 6;
    private static final int INNER_PADDING = 8;
    private static final int HEADER_H = 18;
    private static final int WEEKDAY_H = 14;
    private static final int FOOTER_H = 18; // bottom action row (Today button)
    private static final int CELL_SIZE = 22;
    private static final int CELL_GAP = 2;
    private static final int NAV_BTN_W = 18;
    private static final int NAV_BTN_H = 18;
    private static final int TODAY_BTN_H = 14;
    private static final int MAX_PANEL_W_MARGIN = 40;

    // ── Palette ────────────────────────────────────────────────────────────
    private static final int FRAME_OUTER     = 0xFF3E2510;
    private static final int FRAME_MID       = 0xFF6B4422;
    private static final int FRAME_HIGHLIGHT = 0xFF9B7140;
    private static final int FRAME_INSET     = 0xFF2A1808;
    private static final int PARCHMENT       = 0xFFE8D5A8;
    private static final int PARCHMENT_EDGE  = 0xFFC9B07C;
    private static final int CELL_FILL       = 0xFFF0DDA8;
    private static final int CELL_BORDER     = 0xFFB8985C;
    private static final int CELL_HOVER      = 0xFFFFF0C0;
    private static final int CELL_OTHER_MONTH = 0xFFD8C28F; // desaturated for non-current month view
    private static final int TODAY_FILL      = 0xFFFFB347;
    private static final int TODAY_BORDER    = 0xFF8C4A0E;
    private static final int NAV_BG          = 0xFFD8C28F;
    private static final int NAV_BG_HOVER    = 0xFFEEDDA8;
    private static final int NAV_BORDER      = 0xFF8B6F47;
    private static final int TEXT_HEADER     = 0xFF1F1305;
    private static final int TEXT_WEEKDAY    = 0xFF3A2410;
    private static final int TEXT_DAY        = 0xFF3A2410;
    private static final int TEXT_DAY_DIM    = 0xFF6E5430;
    private static final int TEXT_TODAY      = 0xFF1F1305;
    private static final int TEXT_NAV        = 0xFF3E2510;
    private static final int TEXT_NAV_DIS    = 0xFF8B6F47;
    // Today button: warm gold accent matching the today-cell theme
    private static final int TODAY_BTN_FILL  = 0xFFFFB347;
    private static final int TODAY_BTN_HOVER = 0xFFFFD27A;
    private static final int TODAY_BTN_BORDER = 0xFF8C4A0E;
    private static final int TODAY_BTN_TEXT  = 0xFF1F1305;

    // ── State ──────────────────────────────────────────────────────────────
    private int viewYear;
    private int viewMonth;
    private double scrollX;
    private double maxScrollX;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;

    private record HitRect(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
    private final List<HitRect> hits = new ArrayList<>();
    // Tracks the day cell mouse is hovering over for tooltip rendering.
    private int hoverDay = -1;
    private int hoverDow = -1;

    public CalendarScreen() {
        super(Component.translatable("gui.townstead_calendar.title"));
    }

    @Override
    protected void init() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) {
            viewYear = 1;
            viewMonth = 1;
        } else {
            viewYear = snap.year();
            viewMonth = Math.max(1, snap.monthIndex());
        }
        relayout();
    }

    private void relayout() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        int dpw = (snap != null) ? Math.max(1, snap.daysPerWeek()) : 7;
        int rows = computeRowCount(snap);

        int gridW = dpw * CELL_SIZE + (dpw - 1) * CELL_GAP;
        int gridH = rows * CELL_SIZE + (rows - 1) * CELL_GAP;

        contentW = gridW;
        contentH = HEADER_H + WEEKDAY_H + gridH + FOOTER_H;

        // Width is capped (long weeks scroll horizontally). Height is NOT
        // capped — clipping rows is a real bug; better to let the panel be
        // tall and trust the player's window to accommodate. Tiny windows
        // would need vertical scroll which is future work.
        int maxContentW = Math.max(160, width - MAX_PANEL_W_MARGIN - 2 * (FRAME_THICKNESS + INNER_PADDING));
        contentW = Math.min(contentW, maxContentW);

        // Enforce a minimum width so the header text + nav buttons fit
        int minHeaderW = 2 * NAV_BTN_W + 2 * NAV_BTN_W + 16 + font.width("MMMMMMMMMMMM");
        if (contentW < minHeaderW) contentW = minHeaderW;

        panelW = contentW + 2 * (FRAME_THICKNESS + INNER_PADDING);
        panelH = contentH + 2 * (FRAME_THICKNESS + INNER_PADDING);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        contentX = panelX + FRAME_THICKNESS + INNER_PADDING;
        contentY = panelY + FRAME_THICKNESS + INNER_PADDING;

        maxScrollX = Math.max(0, gridW - contentW);
        if (scrollX > maxScrollX) scrollX = maxScrollX;
        if (scrollX < 0) scrollX = 0;
    }

    private int computeRowCount(@Nullable CalendarClientStore.Snapshot snap) {
        if (snap == null || snap.months().isEmpty()) return 1;
        int monthIdx = Math.max(0, Math.min(viewMonth - 1, snap.months().size() - 1));
        int monthDays = snap.months().get(monthIdx).days();
        int dpw = Math.max(1, snap.daysPerWeek());
        int startDow = startDayOfWeek(snap, viewYear, monthIdx);
        return Math.max(1, (startDow + monthDays + dpw - 1) / dpw);
    }

    private void navigateMonth(int delta) {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null || snap.months().isEmpty()) return;
        int monthCount = snap.months().size();
        int next = viewMonth + delta;
        while (next < 1) { next += monthCount; viewYear = Math.max(1, viewYear - 1); }
        while (next > monthCount) { next -= monthCount; viewYear = viewYear + 1; }
        viewMonth = next;
        relayout();
    }

    private void jumpToToday() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) return;
        viewYear = snap.year();
        viewMonth = Math.max(1, snap.monthIndex());
        relayout();
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScrollX > 0 && isMouseOverGrid(mx, my)) {
            scrollX = clampScroll(scrollX - dy * 16.0);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScrollX > 0 && isMouseOverGrid(mx, my)) {
            scrollX = clampScroll(scrollX - delta * 16.0);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
    *///?}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (HitRect h : hits) {
                if (h.contains(mx, my)) {
                    h.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private double clampScroll(double v) {
        if (v < 0) return 0;
        if (v > maxScrollX) return maxScrollX;
        return v;
    }

    private boolean isMouseOverGrid(double mx, double my) {
        int gridY = contentY + HEADER_H + WEEKDAY_H;
        int gridBottom = contentY + contentH - FOOTER_H;
        return mx >= contentX && mx < contentX + contentW
                && my >= gridY && my < gridBottom;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        hoverDay = -1;
        hoverDow = -1;

        renderFrame(g);

        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) {
            drawCenteredNoShadow(g, Component.translatable("gui.townstead_calendar.no_data"),
                    panelX + panelW / 2, panelY + panelH / 2 - 4, TEXT_DAY);
            return;
        }
        renderHeader(g, snap, mouseX, mouseY);
        renderTodayButton(g, snap, mouseX, mouseY);
        renderWeekdayLabels(g, snap);
        renderGrid(g, snap, mouseX, mouseY);
        renderHoverTooltip(g, snap, mouseX, mouseY);
    }

    private void renderFrame(GuiGraphics g) {
        int x0 = panelX, y0 = panelY, x1 = panelX + panelW, y1 = panelY + panelH;
        g.fill(x0, y0, x1, y0 + 1, FRAME_OUTER);
        g.fill(x0, y1 - 1, x1, y1, FRAME_OUTER);
        g.fill(x0, y0, x0 + 1, y1, FRAME_OUTER);
        g.fill(x1 - 1, y0, x1, y1, FRAME_OUTER);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FRAME_MID);
        int hx0 = x0 + FRAME_THICKNESS - 2;
        int hy0 = y0 + FRAME_THICKNESS - 2;
        int hx1 = x1 - FRAME_THICKNESS + 2;
        int hy1 = y1 - FRAME_THICKNESS + 2;
        g.fill(hx0, hy0, hx1, hy0 + 1, FRAME_HIGHLIGHT);
        g.fill(hx0, hy0, hx0 + 1, hy1, FRAME_HIGHLIGHT);
        g.fill(hx0, hy1 - 1, hx1, hy1, FRAME_INSET);
        g.fill(hx1 - 1, hy0, hx1, hy1, FRAME_INSET);
        int px0 = x0 + FRAME_THICKNESS;
        int py0 = y0 + FRAME_THICKNESS;
        int px1 = x1 - FRAME_THICKNESS;
        int py1 = y1 - FRAME_THICKNESS;
        g.fill(px0, py0, px1, py1, FRAME_INSET);
        g.fill(px0 + 1, py0 + 1, px1 - 1, py1 - 1, PARCHMENT);
        g.fill(px0 + 1, py0 + 1, px1 - 1, py0 + 2, PARCHMENT_EDGE);
        g.fill(px0 + 1, py0 + 1, px0 + 2, py1 - 1, PARCHMENT_EDGE);
    }

    private void renderHeader(GuiGraphics g, CalendarClientStore.Snapshot snap, int mouseX, int mouseY) {
        int headerY = contentY;

        // Nav buttons: «  ‹  ...  ›  »
        int btnY = headerY;
        drawNavButton(g, contentX, btnY, "<<", mouseX, mouseY, () -> {
            viewYear = Math.max(1, viewYear - 1); relayout();
        });
        drawNavButton(g, contentX + NAV_BTN_W + 2, btnY, "<", mouseX, mouseY, () -> navigateMonth(-1));
        drawNavButton(g, contentX + contentW - NAV_BTN_W, btnY, ">>", mouseX, mouseY, () -> {
            viewYear = viewYear + 1; relayout();
        });
        drawNavButton(g, contentX + contentW - 2 * NAV_BTN_W - 2, btnY, ">", mouseX, mouseY, () -> navigateMonth(1));

        // Month + year title
        Component monthName = monthNameFor(snap, viewMonth - 1);
        int displayYear = viewYear;
        Component yearLabel = Component.empty();
        CalendarClientStore.EraResolved era = snap.resolveEra(viewYear);
        if (era != null) {
            displayYear = era.displayedYear();
            yearLabel = era.nameComponent();
        } else if (snap.hasYearSuffix()) {
            yearLabel = snap.yearSuffixComponent();
        }
        String suffixStr = yearLabel.getString();
        String headerStr = monthName.getString() + "  " + displayYear
                + (suffixStr.isEmpty() ? "" : " " + suffixStr);
        int textY = headerY + (HEADER_H - font.lineHeight) / 2;
        // Single draw with vanilla MC shadow — crisp pixel-font readability
        drawCenteredNoShadow(g, headerStr, contentX + contentW / 2, textY, TEXT_HEADER);
    }

    /**
     * Right-anchored gold pill in the bottom footer row of the parchment.
     * Shown only when the viewed month isn't the current one. Footer height
     * is constant so showing/hiding the pill doesn't shift the rest of the
     * layout, and "snap back to today" reads naturally as a footer action.
     */
    private void renderTodayButton(GuiGraphics g, CalendarClientStore.Snapshot snap, int mouseX, int mouseY) {
        boolean isCurrent = (viewYear == snap.year() && viewMonth == snap.monthIndex());
        if (isCurrent) return;

        String label = Component.translatableWithFallback(
                "gui.townstead_calendar.button.today", "Today").getString();
        int btnW = font.width(label) + 14;
        int btnH = TODAY_BTN_H;
        int btnX = contentX + contentW - btnW;
        int btnY = contentY + contentH - FOOTER_H + (FOOTER_H - btnH) / 2;

        boolean hover = mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;
        int bg = hover ? TODAY_BTN_HOVER : TODAY_BTN_FILL;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        drawRectBorder(g, btnX, btnY, btnW, btnH, TODAY_BTN_BORDER);
        drawCenteredNoShadow(g, label, btnX + btnW / 2, btnY + (btnH - font.lineHeight) / 2 + 1, TODAY_BTN_TEXT);
        hits.add(new HitRect(btnX, btnY, btnW, btnH, this::jumpToToday));
    }

    private void renderWeekdayLabels(GuiGraphics g, CalendarClientStore.Snapshot snap) {
        int dpw = Math.max(1, snap.daysPerWeek());
        int labelY = contentY + HEADER_H;
        g.enableScissor(contentX, labelY, contentX + contentW, labelY + WEEKDAY_H);
        boolean named = snap.hasWeekdays() && snap.weekdays().size() == dpw;
        for (int col = 0; col < dpw; col++) {
            int cellX = contentX + col * (CELL_SIZE + CELL_GAP) - (int) scrollX;
            String label = named
                    ? snap.weekdays().get(col).shortComponent().getString()
                    : Integer.toString(col + 1);
            drawCenteredNoShadow(g, label, cellX + CELL_SIZE / 2, labelY + (WEEKDAY_H - font.lineHeight) / 2, TEXT_WEEKDAY);
        }
        g.disableScissor();
    }

    private void renderGrid(GuiGraphics g, CalendarClientStore.Snapshot snap, int mouseX, int mouseY) {
        int dpw = Math.max(1, snap.daysPerWeek());
        if (snap.months().isEmpty()) return;
        int safeMonthIdx = Math.max(0, Math.min(viewMonth - 1, snap.months().size() - 1));
        int monthDays = snap.months().get(safeMonthIdx).days();
        int startDow = startDayOfWeek(snap, viewYear, safeMonthIdx);

        int gridTop = contentY + HEADER_H + WEEKDAY_H;
        int gridBottom = contentY + contentH - FOOTER_H;
        g.enableScissor(contentX, gridTop, contentX + contentW, gridBottom);

        boolean isCurrentMonth = (viewYear == snap.year() && viewMonth == snap.monthIndex());
        int todayDom = snap.dayOfMonth();

        for (int d = 1; d <= monthDays; d++) {
            int cellIndex = startDow + d - 1;
            int col = cellIndex % dpw;
            int row = cellIndex / dpw;
            int cellX = contentX + col * (CELL_SIZE + CELL_GAP) - (int) scrollX;
            int cellY = gridTop + row * (CELL_SIZE + CELL_GAP);

            boolean today = isCurrentMonth && d == todayDom;
            boolean hovered = !today && mouseX >= cellX && mouseX < cellX + CELL_SIZE
                    && mouseY >= cellY && mouseY < cellY + CELL_SIZE
                    && mouseY >= gridTop && mouseY < gridBottom;
            if (hovered) { hoverDay = d; hoverDow = col; }

            int fillColor;
            if (today) fillColor = TODAY_FILL;
            else if (hovered) fillColor = CELL_HOVER;
            else fillColor = isCurrentMonth ? CELL_FILL : CELL_OTHER_MONTH;
            int borderColor = today ? TODAY_BORDER : CELL_BORDER;
            int textColor = today ? TEXT_TODAY : (isCurrentMonth ? TEXT_DAY : TEXT_DAY_DIM);

            g.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, fillColor);
            drawRectBorder(g, cellX, cellY, CELL_SIZE, CELL_SIZE, borderColor);

            String num = Integer.toString(d);
            g.drawString(font, num,
                    cellX + (CELL_SIZE - font.width(num)) / 2,
                    cellY + (CELL_SIZE - font.lineHeight) / 2 + 1,
                    textColor, false);
        }
        g.disableScissor();
    }

    private void renderHoverTooltip(GuiGraphics g, CalendarClientStore.Snapshot snap, int mouseX, int mouseY) {
        if (hoverDay < 0) return;
        List<Component> lines = new ArrayList<>();
        // Full date: "Monday, Axolen 4, 1234 A.D."
        Component monthName = monthNameFor(snap, viewMonth - 1);
        int displayYear = viewYear;
        Component yearLabel = Component.empty();
        CalendarClientStore.EraResolved era = snap.resolveEra(viewYear);
        if (era != null) {
            displayYear = era.displayedYear();
            yearLabel = era.nameComponent();
        } else if (snap.hasYearSuffix()) {
            yearLabel = snap.yearSuffixComponent();
        }
        String weekdayStr = "";
        if (snap.hasWeekdays() && hoverDow >= 0 && hoverDow < snap.weekdays().size()) {
            weekdayStr = snap.weekdays().get(hoverDow).longComponent().getString();
        }
        String suffixStr = yearLabel.getString();
        String headerLine = (weekdayStr.isEmpty() ? "" : weekdayStr + ", ")
                + monthName.getString() + " " + hoverDay + ", " + displayYear
                + (suffixStr.isEmpty() ? "" : " " + suffixStr);
        lines.add(Component.literal(headerLine));
        //? if >=1.21 {
        g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        //?} else {
        /*g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        *///?}
    }

    /** Custom parchment-toned nav button. Registers a hit rect for click handling. */
    private void drawNavButton(GuiGraphics g, int x, int y, String glyph, int mouseX, int mouseY, Runnable action) {
        boolean hover = mouseX >= x && mouseX < x + NAV_BTN_W && mouseY >= y && mouseY < y + NAV_BTN_H;
        int bg = hover ? NAV_BG_HOVER : NAV_BG;
        g.fill(x, y, x + NAV_BTN_W, y + NAV_BTN_H, bg);
        drawRectBorder(g, x, y, NAV_BTN_W, NAV_BTN_H, NAV_BORDER);
        int textColor = TEXT_NAV;
        g.drawString(font, glyph,
                x + (NAV_BTN_W - font.width(glyph)) / 2,
                y + (NAV_BTN_H - font.lineHeight) / 2 + 1,
                textColor, false);
        hits.add(new HitRect(x, y, NAV_BTN_W, NAV_BTN_H, action));
    }

    /** Centered string with no shadow — matches the parchment aesthetic. */
    private void drawCenteredNoShadow(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void drawCenteredNoShadow(GuiGraphics g, Component text, int centerX, int y, int color) {
        String s = text.getString();
        g.drawString(font, s, centerX - font.width(s) / 2, y, color, false);
    }

    private void drawRectBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private int startDayOfWeek(CalendarClientStore.Snapshot snap, int displayYear, int monthIdx) {
        int dpw = Math.max(1, snap.daysPerWeek());
        int dpy = Math.max(1, snap.daysPerYear());
        long yearsElapsed = (long) displayYear - snap.epochYearOffset();
        long startOfYear = yearsElapsed * dpy;
        int daysBefore = 0;
        for (int i = 0; i < monthIdx && i < snap.months().size(); i++) {
            daysBefore += snap.months().get(i).days();
        }
        long startWorldDay = startOfYear + daysBefore;
        return (int) Math.floorMod(startWorldDay, (long) dpw);
    }

    private Component monthNameFor(CalendarClientStore.Snapshot snap, int monthIdxZeroBased) {
        if (monthIdxZeroBased < 0 || monthIdxZeroBased >= snap.months().size()) {
            return Component.literal("?");
        }
        return snap.months().get(monthIdxZeroBased).nameComponent();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
