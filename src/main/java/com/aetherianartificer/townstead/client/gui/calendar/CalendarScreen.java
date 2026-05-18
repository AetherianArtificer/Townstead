package com.aetherianartificer.townstead.client.gui.calendar;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.client.gui.fieldpost.FrameRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    // ── Background ─────────────────────────────────────────────────────────
    // Vanilla empty-map texture: 64×64, with a 7-pixel wood-look frame on all
    // sides and a 50×50 parchment interior. 9-sliced so corners stay crisp
    // while the interior stretches to fit our panel.
    //? if neoforge {
    private static final ResourceLocation MAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/map/map_background.png");
    //?} else {
    /*private static final ResourceLocation MAP_TEXTURE =
            new ResourceLocation("minecraft", "textures/map/map_background.png");
    *///?}
    private static final int MAP_TEX_SIZE = 64;
    private static final int MAP_FRAME    = 7;
    // Outer wood-plank frame around the map background. Matches the Field Post
    // UI's plank framing so all Townstead GUIs read as one family.
    private static final int WOOD_FRAME_THICKNESS = 7;

    // ── Layout ─────────────────────────────────────────────────────────────
    private static final int FRAME_THICKNESS = MAP_FRAME;
    private static final int INNER_PADDING = 8;
    private static final int HEADER_H = 18;
    private static final int WEEKDAY_H = 14;
    // Footer holds the Today button. Sized so the button sits with roughly
    // equal padding above (to the last grid row) and below (to the parchment
    // inner edge, which extends INNER_PADDING beyond the content area).
    private static final int FOOTER_H = 26;
    // Cells are sized to leave room for a day number (top-left) plus future
    // content underneath — birthday-villager faces, anniversary glyphs, etc.
    private static final int CELL_SIZE = 32;
    private static final int CELL_GAP = 2;
    // Inset for the day number from the cell's top-left corner.
    private static final int DAY_NUM_PAD = 3;
    private static final int NAV_BTN_W = 18;
    private static final int NAV_BTN_H = 18;
    private static final int MAX_PANEL_W_MARGIN = 40;

    // ── Palette ────────────────────────────────────────────────────────────
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
        int chromeW = 2 * (FRAME_THICKNESS + INNER_PADDING + WOOD_FRAME_THICKNESS);
        int maxContentW = Math.max(160, width - MAX_PANEL_W_MARGIN - chromeW);
        contentW = Math.min(contentW, maxContentW);

        // Enforce a minimum width so the header text + nav buttons fit
        int minHeaderW = 2 * NAV_BTN_W + 2 * NAV_BTN_W + 16 + font.width("MMMMMMMMMMMM");
        if (contentW < minHeaderW) contentW = minHeaderW;

        panelW = contentW + 2 * (FRAME_THICKNESS + INNER_PADDING);
        panelH = contentH + 2 * (FRAME_THICKNESS + INNER_PADDING);
        int totalW = panelW + 2 * WOOD_FRAME_THICKNESS;
        int totalH = panelH + 2 * WOOD_FRAME_THICKNESS;
        panelX = (width - totalW) / 2 + WOOD_FRAME_THICKNESS;
        panelY = (height - totalH) / 2 + WOOD_FRAME_THICKNESS;
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

        renderPlankFrame(g);
        renderMapBackground(g);

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

    /**
     * Wood-plank frame that wraps the map panel. Same plank texture and outer
     * bevel as Field Post's {@link FrameRenderer#drawWoodenFrame}, but with no
     * inner shadow line — the map texture already has a baked wood-frame edge,
     * and a black 1px stroke against it reads as an ugly seam. The full outer
     * rect is tiled with planks so any halo around the map's transparent
     * edges blends into wood rather than the screen dim.
     */
    private void renderPlankFrame(GuiGraphics g) {
        int outX = panelX - WOOD_FRAME_THICKNESS;
        int outY = panelY - WOOD_FRAME_THICKNESS;
        int outW = panelW + 2 * WOOD_FRAME_THICKNESS;
        int outH = panelH + 2 * WOOD_FRAME_THICKNESS;

        FrameRenderer.tileTexture(g, FrameRenderer.PLANK_DARK, outX, outY, outW, outH);

        // Outer shadow line — separates the frame from the screen dim.
        g.fill(outX - 1, outY - 1, outX + outW + 1, outY,          FrameRenderer.FRAME_SHADOW);
        g.fill(outX - 1, outY + outH, outX + outW + 1, outY + outH + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(outX - 1, outY, outX, outY + outH,                  FrameRenderer.FRAME_SHADOW);
        g.fill(outX + outW, outY, outX + outW + 1, outY + outH,    FrameRenderer.FRAME_SHADOW);

        // Top/left highlight for the bevel.
        g.fill(outX, outY, outX + outW, outY + 1, FrameRenderer.FRAME_HIGHLIGHT);
        g.fill(outX, outY, outX + 1, outY + outH, FrameRenderer.FRAME_HIGHLIGHT);
    }

    /**
     * 9-slice the vanilla empty-map texture across the panel. Corners are
     * blitted at native pixel size so the wood-grain frame doesn't distort;
     * the four edges and the parchment interior stretch to fit.
     */
    private void renderMapBackground(GuiGraphics g) {
        final int x = panelX, y = panelY;
        final int w = panelW, h = panelH;
        final int f = MAP_FRAME;
        final int t = MAP_TEX_SIZE;
        final int srcInner = t - 2 * f;   // 50: width/height of the texture's parchment strip
        final int dstInnerW = w - 2 * f;
        final int dstInnerH = h - 2 * f;

        // Corners (1:1)
        blitSlice(g, x,         y,         f, f,        0,        0,        f,        f);
        blitSlice(g, x + w - f, y,         f, f,        t - f,    0,        f,        f);
        blitSlice(g, x,         y + h - f, f, f,        0,        t - f,    f,        f);
        blitSlice(g, x + w - f, y + h - f, f, f,        t - f,    t - f,    f,        f);

        // Edges (stretch the middle strip of the texture along one axis)
        blitSlice(g, x + f,     y,         dstInnerW, f, f,        0,        srcInner, f);
        blitSlice(g, x + f,     y + h - f, dstInnerW, f, f,        t - f,    srcInner, f);
        blitSlice(g, x,         y + f,     f, dstInnerH, 0,        f,        f,        srcInner);
        blitSlice(g, x + w - f, y + f,     f, dstInnerH, t - f,    f,        f,        srcInner);

        // Parchment interior (stretch both axes)
        blitSlice(g, x + f, y + f, dstInnerW, dstInnerH, f, f, srcInner, srcInner);
    }

    private void blitSlice(GuiGraphics g, int x, int y, int dw, int dh,
                           int u, int v, int sw, int sh) {
        g.blit(MAP_TEXTURE, x, y, dw, dh, (float) u, (float) v, sw, sh, MAP_TEX_SIZE, MAP_TEX_SIZE);
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
     * Right-anchored Today button. Same fill/border palette as the header nav
     * arrows so all the interactive parchment chrome reads as one family.
     * Shown only when the viewed month isn't the current one. Footer height
     * is constant so showing/hiding the button doesn't shift layout.
     */
    private void renderTodayButton(GuiGraphics g, CalendarClientStore.Snapshot snap, int mouseX, int mouseY) {
        boolean isCurrent = (viewYear == snap.year() && viewMonth == snap.monthIndex());
        if (isCurrent) return;

        String label = Component.translatableWithFallback(
                "gui.townstead_calendar.button.today", "Today").getString();
        int btnW = font.width(label) + 14;
        int btnH = NAV_BTN_H;
        int btnX = contentX + contentW - btnW;
        // Center vertically in the full strip from grid bottom to the parchment
        // inner edge (which sits INNER_PADDING below contentY+contentH), so the
        // gap above and below the button is roughly equal.
        int gridBottom = contentY + contentH - FOOTER_H;
        int parchmentBottom = contentY + contentH + INNER_PADDING;
        int btnY = gridBottom + ((parchmentBottom - gridBottom) - btnH) / 2;

        boolean hover = mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;
        int bg = hover ? NAV_BG_HOVER : NAV_BG;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        drawRectBorder(g, btnX, btnY, btnW, btnH, NAV_BORDER);
        g.drawString(font, label,
                btnX + (btnW - font.width(label)) / 2,
                btnY + (btnH - font.lineHeight) / 2 + 1,
                TEXT_NAV, false);
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
                    cellX + DAY_NUM_PAD,
                    cellY + DAY_NUM_PAD,
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
