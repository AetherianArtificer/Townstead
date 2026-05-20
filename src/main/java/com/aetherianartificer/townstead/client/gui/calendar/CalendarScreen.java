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
    // Header packs three things: a small year/era subtitle, a 2×-scaled month
    // name underneath, and nav arrows flanking. Sized to fit both text lines
    // plus a touch of breathing room above/below.
    private static final int MONTH_SCALE = 2;
    private static final int HEADER_H = 34;
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
    // Vertical breathing room left between the panel and the window edges so
    // the calendar never butts right up against the top/bottom of the screen.
    private static final int MAX_PANEL_H_MARGIN = 20;
    // Lower bound on the uniform UI scale. Going below this makes the text
    // unreadable; better to clip off-screen than to render a blurry mess.
    private static final float MIN_UI_SCALE = 0.4f;

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
    // Uniform scale applied to the entire UI as a single pose transform.
    // <1.0 when the window is too small to fit the design-size panel.
    // All layout coordinates (panelX/Y, contentX/Y, hit rects) are stored in
    // VIRTUAL pre-scale space; mouse positions get divided by uiScale before
    // hit-testing.
    private float uiScale = 1f;

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

        int gridW = dpw  * CELL_SIZE + (dpw  - 1) * CELL_GAP;
        int gridH = rows * CELL_SIZE + (rows - 1) * CELL_GAP;

        contentW = gridW;
        contentH = HEADER_H + WEEKDAY_H + gridH + FOOTER_H;

        // Enforce a minimum width so the 2× month name + nav button cluster fit
        int minHeaderW = 4 * NAV_BTN_W + 8 + font.width("MMMMMMMMMMMM") * MONTH_SCALE + 16;
        if (contentW < minHeaderW) contentW = minHeaderW;

        panelW = contentW + 2 * (FRAME_THICKNESS + INNER_PADDING);
        panelH = contentH + 2 * (FRAME_THICKNESS + INNER_PADDING);
        int totalW = panelW + 2 * WOOD_FRAME_THICKNESS;
        int totalH = panelH + 2 * WOOD_FRAME_THICKNESS;

        // Compute the single uniform UI scale so the design-size panel fits
        // the current window with margin on each side. Capped at 1.0 (we don't
        // up-scale beyond the design size on large monitors — that would
        // produce blurry text — but we do shrink to fit on small ones).
        float scaleW = (width  - MAX_PANEL_W_MARGIN) / (float) totalW;
        float scaleH = (height - MAX_PANEL_H_MARGIN) / (float) totalH;
        uiScale = Math.min(1f, Math.min(scaleW, scaleH));
        uiScale = Math.max(MIN_UI_SCALE, uiScale);

        // Panel position is stored in VIRTUAL (pre-scale) coordinates. After
        // pose.scale(uiScale) maps those coords to screen pixels, the panel
        // should appear centred. Solving (panelX_v * uiScale) = screenX yields
        // panelX_v = screenX / uiScale.
        int scaledTotalW = Math.round(totalW * uiScale);
        int scaledTotalH = Math.round(totalH * uiScale);
        int virtualPanelLeft = Math.round((width  - scaledTotalW) * 0.5f / uiScale);
        int virtualPanelTop  = Math.round((height - scaledTotalH) * 0.5f / uiScale);
        panelX = virtualPanelLeft + WOOD_FRAME_THICKNESS;
        panelY = virtualPanelTop  + WOOD_FRAME_THICKNESS;
        contentX = panelX + FRAME_THICKNESS + INNER_PADDING;
        contentY = panelY + FRAME_THICKNESS + INNER_PADDING;

        maxScrollX = Math.max(0, gridW - contentW);
        if (scrollX > maxScrollX) scrollX = maxScrollX;
        if (scrollX < 0) scrollX = 0;
    }

    private int computeRowCount(@Nullable CalendarClientStore.Snapshot snap) {
        if (snap == null || snap.months().isEmpty()) return 1;
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> yearMonths = snap.monthsForYear(viewYear);
        if (yearMonths.isEmpty()) return 1;
        int monthIdx = Math.max(0, Math.min(viewMonth - 1, yearMonths.size() - 1));
        int monthDays = yearMonths.get(monthIdx).days();
        int dpw = Math.max(1, snap.daysPerWeek());
        int startDow = startDayOfWeek(snap, viewYear, monthIdx);
        return Math.max(1, (startDow + monthDays + dpw - 1) / dpw);
    }

    private void navigateMonth(int delta) {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null || snap.months().isEmpty()) return;
        int monthCount = snap.monthsForYear(viewYear).size();
        if (monthCount == 0) return;
        int next = viewMonth + delta;
        while (next < 1) {
            viewYear = Math.max(1, viewYear - 1);
            int prevCount = snap.monthsForYear(viewYear).size();
            if (prevCount <= 0) break;
            next += prevCount;
            monthCount = snap.monthsForYear(viewYear).size();
        }
        while (next > monthCount) {
            next -= monthCount;
            viewYear = viewYear + 1;
            monthCount = snap.monthsForYear(viewYear).size();
            if (monthCount <= 0) break;
        }
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
        if (maxScrollX > 0 && isMouseOverGrid(mx / uiScale, my / uiScale)) {
            scrollX = clampScroll(scrollX - dy * 16.0);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScrollX > 0 && isMouseOverGrid(mx / uiScale, my / uiScale)) {
            scrollX = clampScroll(scrollX - delta * 16.0);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
    *///?}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            double vmx = mx / uiScale;
            double vmy = my / uiScale;
            for (HitRect h : hits) {
                if (h.contains(vmx, vmy)) {
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

        // Every interactive coord (hit rects, hover detection) lives in
        // virtual (pre-scale) space; remap the real mouse position so hover
        // highlights line up with what the player actually sees on screen.
        int vmx = Math.round(mouseX / uiScale);
        int vmy = Math.round(mouseY / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        renderPlankFrame(g);
        renderMapBackground(g);

        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) {
            drawCenteredNoShadow(g, Component.translatable("gui.townstead_calendar.no_data"),
                    panelX + panelW / 2, panelY + panelH / 2 - 4, TEXT_DAY);
            g.pose().popPose();
            return;
        }
        renderHeader(g, snap, vmx, vmy);
        renderTodayButton(g, snap, vmx, vmy);
        renderWeekdayLabels(g, snap);
        renderGrid(g, snap, vmx, vmy);

        g.pose().popPose();

        // Tooltip renders OUTSIDE the scale block so the tooltip text stays
        // at native font size regardless of UI scale, and follows the real
        // mouse cursor position.
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

        tilePlankScaled(g, FrameRenderer.PLANK_DARK, outX, outY, outW, outH);

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

        // Year subtitle on top, small.
        String suffixStr = yearLabel.getString();
        String yearStr = displayYear + (suffixStr.isEmpty() ? "" : " " + suffixStr);
        int yearY = headerY + 2;
        drawCenteredNoShadow(g, yearStr, contentX + contentW / 2, yearY, TEXT_HEADER);

        // Month name underneath, 2× scaled via the pose matrix so it reads as
        // the dominant heading. Scaling happens around (0,0), so draw coords
        // are divided by MONTH_SCALE to land at the intended screen position.
        String monthStr = monthName.getString();
        int monthScaledW = font.width(monthStr) * MONTH_SCALE;
        int monthScaledH = font.lineHeight * MONTH_SCALE;
        int monthScreenX = contentX + (contentW - monthScaledW) / 2;
        int monthScreenY = yearY + font.lineHeight + 2;
        g.pose().pushPose();
        g.pose().scale(MONTH_SCALE, MONTH_SCALE, 1.0f);
        g.drawString(font, monthStr,
                monthScreenX / MONTH_SCALE,
                monthScreenY / MONTH_SCALE,
                TEXT_HEADER, false);
        g.pose().popPose();

        // Nav buttons sit flush with the bottom of the (scaled) month name —
        // they navigate the month, so visually anchoring them to its baseline
        // reads more intentionally than centering on the whole header.
        int navY = monthScreenY + monthScaledH - NAV_BTN_H;
        drawNavButton(g, contentX, navY, "<<", mouseX, mouseY, () -> {
            viewYear = Math.max(1, viewYear - 1); relayout();
        });
        drawNavButton(g, contentX + NAV_BTN_W + 2, navY, "<", mouseX, mouseY, () -> navigateMonth(-1));
        drawNavButton(g, contentX + contentW - NAV_BTN_W, navY, ">>", mouseX, mouseY, () -> {
            viewYear = viewYear + 1; relayout();
        });
        drawNavButton(g, contentX + contentW - 2 * NAV_BTN_W - 2, navY, ">", mouseX, mouseY, () -> navigateMonth(1));
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
        enableVirtualScissor(g, contentX, labelY, contentX + contentW, labelY + WEEKDAY_H);
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
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> yearMonths = snap.monthsForYear(viewYear);
        if (yearMonths.isEmpty()) return;
        int safeMonthIdx = Math.max(0, Math.min(viewMonth - 1, yearMonths.size() - 1));
        int monthDays = yearMonths.get(safeMonthIdx).days();
        int startDow = startDayOfWeek(snap, viewYear, safeMonthIdx);

        int gridTop = contentY + HEADER_H + WEEKDAY_H;
        int gridBottom = contentY + contentH - FOOTER_H;
        enableVirtualScissor(g, contentX, gridTop, contentX + contentW, gridBottom);

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

    /**
     * Set a scissor rectangle in VIRTUAL coords (the same coord space the rest
     * of the screen draws in). {@link GuiGraphics#enableScissor} ignores the
     * current pose matrix and treats its rect as raw screen pixels, so when
     * the whole screen is wrapped in {@code pose.scale(uiScale, uiScale, 1)}
     * we'd otherwise get a mismatch between where the scissor sits and where
     * the geometry actually lands — clipping out things that should be on
     * screen. Floor/ceil to avoid 1-pixel seams along the scaled edge.
     */
    /**
     * Mirror of {@link FrameRenderer#tileTexture} but routed through
     * {@link #enableVirtualScissor} so the scissor lines up with the scaled
     * geometry inside our pose.scale block. Without this, the scissor sits at
     * the unscaled virtual coords (raw screen pixels), which doesn't match
     * where the tiled planks actually land on screen — producing a partially
     * cropped frame that doesn't wrap the parchment evenly.
     */
    private void tilePlankScaled(GuiGraphics g, String texture, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        final int tileSize = 16;
        enableVirtualScissor(g, x, y, x + w, y + h);
        for (int ty = 0; ty < h; ty += tileSize) {
            for (int tx = 0; tx < w; tx += tileSize) {
                com.aetherianartificer.townstead.client.gui.fieldpost.CellTextures
                        .blit(g, texture, x + tx, y + ty, tileSize);
            }
        }
        //? if <1.21 {
        /*g.flush();
        *///?}
        g.disableScissor();
    }

    private void enableVirtualScissor(GuiGraphics g, int vx1, int vy1, int vx2, int vy2) {
        g.enableScissor(
                (int) Math.floor(vx1 * uiScale),
                (int) Math.floor(vy1 * uiScale),
                (int) Math.ceil(vx2 * uiScale),
                (int) Math.ceil(vy2 * uiScale));
    }

    private void drawRectBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private int startDayOfWeek(CalendarClientStore.Snapshot snap, int displayYear, int monthIdx) {
        int dpw = Math.max(1, snap.daysPerWeek());
        long startOfYear = snap.worldDayAtYearStart(displayYear);
        int daysBefore = snap.daysBeforeMonth(displayYear, monthIdx + 1);
        long startWorldDay = startOfYear + daysBefore;
        return (int) Math.floorMod(startWorldDay, (long) dpw);
    }

    private Component monthNameFor(CalendarClientStore.Snapshot snap, int monthIdxZeroBased) {
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> yearMonths = snap.monthsForYear(viewYear);
        if (monthIdxZeroBased < 0 || monthIdxZeroBased >= yearMonths.size()) {
            return Component.literal("?");
        }
        return yearMonths.get(monthIdxZeroBased).commonName();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
