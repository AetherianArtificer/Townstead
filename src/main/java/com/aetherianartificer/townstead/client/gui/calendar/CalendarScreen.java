package com.aetherianartificer.townstead.client.gui.calendar;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Stardew-style month-grid calendar. Reads the active profile shape from
 * {@link CalendarClientStore} (months list, days_per_week, year suffix,
 * epoch) so it can render any month/year locally without server round-trips
 * per navigation.
 *
 * Grid layout:
 * <ul>
 *   <li>Columns = {@code days_per_week} from the profile. Cells are a fixed
 *       size, so very long weeks (e.g., Mayan 13-day week) overflow the
 *       panel and the user can scroll horizontally.</li>
 *   <li>Rows = enough to fit the displayed month given the start-of-month
 *       day-of-week offset.</li>
 *   <li>"Today" cell is highlighted when the viewed month is the current
 *       one.</li>
 * </ul>
 *
 * Phase 1 ships pure structure; phase 2 will overlay village events
 * (birthdays, festivals) when that data exists server-side.
 */
public class CalendarScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 200;
    private static final int CELL_SIZE = 22;
    private static final int CELL_GAP = 2;
    private static final int HEADER_H = 32;
    private static final int WEEK_LABEL_H = 14;
    private static final int GRID_PADDING = 8;

    /** Year being viewed (display year, includes epoch offset). */
    private int viewYear;
    /** Month index being viewed (1-based to match CalendarDate convention). */
    private int viewMonth;

    /** Horizontal scroll for very long weeks, in pixels. 0 = left-aligned. */
    private double scrollX;
    private double maxScrollX;

    private int panelX;
    private int panelY;

    @Nullable
    private Button prevMonthBtn;
    @Nullable
    private Button nextMonthBtn;
    @Nullable
    private Button prevYearBtn;
    @Nullable
    private Button nextYearBtn;

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

        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        int btnY = panelY + 8;
        prevYearBtn = addRenderableWidget(Button.builder(
                Component.literal("«"),
                b -> { viewYear = Math.max(1, viewYear - 1); recomputeScrollBounds(); })
                .bounds(panelX + 6, btnY, 16, 16).build());
        prevMonthBtn = addRenderableWidget(Button.builder(
                Component.literal("‹"),
                b -> navigateMonth(-1))
                .bounds(panelX + 24, btnY, 16, 16).build());
        nextMonthBtn = addRenderableWidget(Button.builder(
                Component.literal("›"),
                b -> navigateMonth(1))
                .bounds(panelX + PANEL_W - 40, btnY, 16, 16).build());
        nextYearBtn = addRenderableWidget(Button.builder(
                Component.literal("»"),
                b -> { viewYear = viewYear + 1; recomputeScrollBounds(); })
                .bounds(panelX + PANEL_W - 22, btnY, 16, 16).build());

        recomputeScrollBounds();
    }

    private void navigateMonth(int delta) {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null || snap.months().isEmpty()) return;
        int monthCount = snap.months().size();
        int next = viewMonth + delta;
        while (next < 1) { next += monthCount; viewYear = Math.max(1, viewYear - 1); }
        while (next > monthCount) { next -= monthCount; viewYear = viewYear + 1; }
        viewMonth = next;
        recomputeScrollBounds();
    }

    private void recomputeScrollBounds() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) { maxScrollX = 0; return; }
        int dpw = Math.max(1, snap.daysPerWeek());
        int gridInnerW = PANEL_W - 2 * GRID_PADDING;
        int neededW = dpw * (CELL_SIZE + CELL_GAP) - CELL_GAP;
        maxScrollX = Math.max(0, neededW - gridInnerW);
        if (scrollX > maxScrollX) scrollX = maxScrollX;
        if (scrollX < 0) scrollX = 0;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScrollX > 0 && isMouseOverGrid(mx, my)) {
            scrollX -= dy * 16.0;
            if (scrollX < 0) scrollX = 0;
            if (scrollX > maxScrollX) scrollX = maxScrollX;
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScrollX > 0 && isMouseOverGrid(mx, my)) {
            scrollX -= delta * 16.0;
            if (scrollX < 0) scrollX = 0;
            if (scrollX > maxScrollX) scrollX = maxScrollX;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
    *///?}

    private boolean isMouseOverGrid(double mx, double my) {
        int gridY = panelY + HEADER_H + WEEK_LABEL_H;
        return mx >= panelX + GRID_PADDING && mx < panelX + PANEL_W - GRID_PADDING
                && my >= gridY && my < panelY + PANEL_H - GRID_PADDING;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // Background panel
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xF0202020);
        g.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xFF8B6F47);

        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) {
            g.drawCenteredString(font, Component.translatable("gui.townstead_calendar.no_data"),
                    panelX + PANEL_W / 2, panelY + PANEL_H / 2 - 4, 0xFFCCCCCC);
            return;
        }

        renderHeader(g, snap);
        renderWeekdayLabels(g, snap);
        renderGrid(g, snap, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g, CalendarClientStore.Snapshot snap) {
        // "MonthName Year [Suffix]"
        Component monthName = monthNameFor(snap, viewMonth - 1);
        Component yearText = snap.hasYearSuffix()
                ? Component.translatable("gui.townstead_calendar.header.year_with_suffix",
                    viewYear, snap.yearSuffixComponent())
                : Component.literal(Integer.toString(viewYear));
        String headerStr = monthName.getString() + "  " + yearText.getString();
        g.drawCenteredString(font, Component.literal(headerStr),
                panelX + PANEL_W / 2, panelY + 12, 0xFFFFFFFF);
    }

    private void renderWeekdayLabels(GuiGraphics g, CalendarClientStore.Snapshot snap) {
        int dpw = Math.max(1, snap.daysPerWeek());
        int labelY = panelY + HEADER_H;
        int gridLeft = panelX + GRID_PADDING;
        int gridInnerW = PANEL_W - 2 * GRID_PADDING;
        // Clip to grid area for horizontal scroll
        g.enableScissor(gridLeft, labelY, gridLeft + gridInnerW, labelY + WEEK_LABEL_H);
        boolean named = snap.hasWeekdays() && snap.weekdays().size() == dpw;
        for (int col = 0; col < dpw; col++) {
            int cellX = gridLeft + col * (CELL_SIZE + CELL_GAP) - (int) scrollX;
            String label = named
                    ? snap.weekdays().get(col).shortComponent().getString()
                    : Integer.toString(col + 1);
            g.drawCenteredString(font, label,
                    cellX + CELL_SIZE / 2, labelY + 2, 0xFFCCCCCC);
        }
        g.disableScissor();
    }

    private void renderGrid(GuiGraphics g, CalendarClientStore.Snapshot snap, int mouseX, int mouseY) {
        int dpw = Math.max(1, snap.daysPerWeek());
        if (snap.months().isEmpty()) return;
        int safeMonthIdx = Math.max(0, Math.min(viewMonth - 1, snap.months().size() - 1));
        int monthDays = snap.months().get(safeMonthIdx).days();

        int startDow = startDayOfWeek(snap, viewYear, safeMonthIdx);

        int gridLeft = panelX + GRID_PADDING;
        int gridTop = panelY + HEADER_H + WEEK_LABEL_H;
        int gridInnerW = PANEL_W - 2 * GRID_PADDING;
        int gridInnerH = PANEL_H - HEADER_H - WEEK_LABEL_H - GRID_PADDING;

        g.enableScissor(gridLeft, gridTop, gridLeft + gridInnerW, gridTop + gridInnerH);

        boolean isCurrentMonth = (viewYear == snap.year() && viewMonth == snap.monthIndex());
        int todayDom = snap.dayOfMonth();

        for (int d = 1; d <= monthDays; d++) {
            int cellIndex = startDow + d - 1;
            int col = cellIndex % dpw;
            int row = cellIndex / dpw;
            int cellX = gridLeft + col * (CELL_SIZE + CELL_GAP) - (int) scrollX;
            int cellY = gridTop + row * (CELL_SIZE + CELL_GAP);
            int bg = (isCurrentMonth && d == todayDom) ? 0xFFFFCC66 : 0xFF3A3A3A;
            int fg = (isCurrentMonth && d == todayDom) ? 0xFF202020 : 0xFFFFFFFF;
            g.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, bg);
            g.renderOutline(cellX, cellY, CELL_SIZE, CELL_SIZE, 0xFF1A1A1A);
            g.drawCenteredString(font, Integer.toString(d),
                    cellX + CELL_SIZE / 2, cellY + (CELL_SIZE - font.lineHeight) / 2, fg);
        }
        g.disableScissor();
    }

    /**
     * Day-of-week index (0-based, mod {@code daysPerWeek}) of the first day
     * of {@code monthIdx} in {@code displayYear}. Walks the profile months
     * to compute the cumulative worldDay at the start of the requested
     * month, then takes mod days_per_week.
     */
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
        long mod = Math.floorMod(startWorldDay, (long) dpw);
        return (int) mod;
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
