package com.aetherianartificer.townstead.client.gui.calendar;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.calendar.CalendarStamp;
import com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload;
import com.aetherianartificer.townstead.calendar.CalendarStampClientStore;
import com.aetherianartificer.townstead.calendar.CalendarStampSavedData;
import com.aetherianartificer.townstead.client.gui.fieldpost.FrameRenderer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // ── Stamps ─────────────────────────────────────────────────────────────
    // Render size (virtual px); art is sampled full-texture into this square.
    private static final int STAMP_SIZE = 16;
    private static final int PALETTE_W = 156;   // drawer width (4 columns + frame)
    private static final int DRAWER_PAD = 7;    // frame inset for drawer content
    private static final int TAB_W = 14;        // pull-tab protrusion
    private static final int TAB_H = 52;        // pull-tab height
    private static final int TAB_MARGIN = 1;    // frame-to-closed-tab gap
    private static final int DRAWER_ANIM_MS = 190; // open/close slide duration
    private static final int OPEN_OVERLAP = 4;  // px the open drawer tucks under the calendar
    private static final int THUMB = 28;        // thumbnail cell size
    private static final int THUMB_GAP = 6;     // gap between thumbnail cells
    private static final int CONTENT_PAD = 6;   // inner padding inside the drawer frame
    private static final int DRAWER_TEXT    = 0xFF3A2410; // dark text on parchment
    private static final int SELECT_BORDER  = 0xFF2D7DD2; // placed-stamp selection (on calendar)
    private static final int HOVER_BORDER   = 0xFFFFE680; // warm hover outline
    private static final int HOVER_GLOW     = 0x40FFE680; // base alpha for the stamp halo

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

    // ── Stamp UI state ─────────────────────────────────────────────────────
    private boolean stampMode = false;
    // Armed palette stamp to place (index into StampCatalog.list()); -1 = none.
    private int selectedPaletteIndex = -1;
    // Selected placed stamp for edit/move; null = place mode.
    @Nullable private UUID selectedStampId = null;
    @Nullable private EditBox captionBox;
    private boolean newStampPublic = false;   // visibility for the next placed stamp
    @Nullable private Button backButton;
    private int paletteX, paletteY, paletteW, paletteH;
    private int paletteXOpen, paletteXClosed;  // paletteX rides between these
    // 0 = closed (tucked behind calendar), 1 = open. Eased per frame.
    private float drawerAnim = 0f;
    private float drawerEase = 0f;
    private long drawerAnimLastMs = 0L;
    // True only when settled fully open; gates body interactivity.
    private boolean drawerInteractive = false;
    private int tabX, tabY, tabW, tabH;        // pull-tab rect (virtual coords)
    private int paletteScroll = 0;
    private int paletteContentH = 0;
    // Drawer section coordinates (virtual), computed in layoutRightPanel.
    private int drGridTop, drGridBottom, drDividerY;
    private int drCaptionLabelY, drCaptionBoxY, drVisLabelY, drVisToggleY;
    private int drDeleteY;
    private boolean hoverVisibilityInfo = false;  // cursor over Visibility label
    private final List<HitRect> paletteHits = new ArrayList<>();
    private record ThumbHit(int x, int y, int w, int h, int index) {}
    private final List<ThumbHit> thumbHits = new ArrayList<>();
    // Drag-to-move: dragGrab* is the grab offset from the stamp's top-left (virtual).
    @Nullable private UUID draggingId = null;
    private double dragVX, dragVY;
    private double dragGrabDX, dragGrabDY;
    private boolean dragMoved = false;
    @Nullable private CalendarStamp hoverStamp = null;  // topmost stamp under cursor
    private int hoverThumbIndex = -1;

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
        initStampWidgets();
    }

    private void initStampWidgets() {
        StampCatalog.refresh();
        backButton = Button.builder(Component.translatable("townstead.gui.back"), b -> onClose())
                .bounds(6, 0, 60, 20)
                .build();
        addRenderableWidget(backButton);

        // NOT addWidget'd: the drawer renders in scaled/virtual space, so vanilla's
        // real-coord input routing would mis-hit. Events are forwarded manually.
        captionBox = new EditBox(font, 0, 0, innerWidth(), 16,
                Component.translatable("gui.townstead_calendar.stamp.caption"));
        captionBox.setMaxLength(CalendarStampSavedData.MAX_CAPTION_LEN);
        captionBox.visible = stampMode;

        layoutRightPanel();
    }

    /**
     * Lay out the slide-out drawer (full calendar height; grid on top, compose
     * block pinned to the bottom). All coords are VIRTUAL so the drawer scales
     * with the calendar.
     */
    private void layoutRightPanel() {
        paletteW = PALETTE_W;
        int calRight  = panelX + panelW + WOOD_FRAME_THICKNESS;
        int calTop    = panelY - WOOD_FRAME_THICKNESS;
        int calBottom = panelY + panelH + WOOD_FRAME_THICKNESS;
        // Back button is an unscaled vanilla widget → real-pixel position.
        if (backButton != null) backButton.setPosition(6, Math.round(calBottom * uiScale) - 20);

        // Open: tucked OPEN_OVERLAP px under the right frame (hides the seam).
        // Closed: slid fully behind the calendar so only the pull tab pokes out.
        paletteXOpen   = calRight - OPEN_OVERLAP;
        paletteXClosed = calRight - paletteW - 2;
        paletteX = stampMode ? paletteXOpen : paletteXClosed;

        paletteY = calTop;
        paletteH = Math.max(120, calBottom - calTop);

        // Bottom-up; Delete (when a stamp is selected) sits below the compose block.
        boolean sel = selectedStampId != null;
        int y = contentBottom() - BOTTOM_PAD;
        if (sel) {
            drDeleteY = y - CTRL_H;
            y = drDeleteY - DELETE_GAP;
        }
        drVisToggleY = y - CTRL_H;
        drVisLabelY = drVisToggleY - 2 - LABEL_H;
        drCaptionBoxY = drVisLabelY - SECTION_GAP - 16;
        drCaptionLabelY = drCaptionBoxY - 2 - LABEL_H;
        drDividerY = drCaptionLabelY - SECTION_GAP;
        drGridTop = paletteY + DRAWER_PAD + GRID_TOP_PAD;
        drGridBottom = drDividerY - SECTION_GAP;

        if (captionBox != null) { captionBox.setPosition(innerLeft(), drCaptionBoxY); captionBox.setWidth(innerWidth()); }
    }

    private static final int LABEL_H = 11;
    private static final int CTRL_H = 16;
    private static final int SECTION_GAP = 7;   // breathing room between drawer sections
    private static final int GRID_TOP_PAD = 6;  // space above the first row of stamps
    private static final int BOTTOM_PAD = 9;    // space below the lowest control to the frame
    private static final int DELETE_GAP = 10;   // separation between visibility and the Delete button

    /** Advances the open/close slide toward the current {@link #stampMode}. */
    private void updateDrawerAnim() {
        long now = Util.getMillis();
        if (drawerAnimLastMs == 0L) drawerAnimLastMs = now;
        float dt = Math.min(0.1f, (now - drawerAnimLastMs) / 1000f);
        drawerAnimLastMs = now;
        float target = stampMode ? 1f : 0f;
        float step = dt * (1000f / DRAWER_ANIM_MS);
        if (drawerAnim < target)      drawerAnim = Math.min(target, drawerAnim + step);
        else if (drawerAnim > target) drawerAnim = Math.max(target, drawerAnim - step);
    }

    /** Smoothstep easing for the slide. */
    private static float ease(float t) { return t * t * (3f - 2f * t); }

    private int iw() { return paletteW - 2 * DRAWER_PAD; }
    private int contentLeft() { return paletteX + DRAWER_PAD; }
    // Padded content area inside the frame; everything aligns to these for even margins.
    private int innerLeft() { return contentLeft() + CONTENT_PAD; }
    private int innerWidth() { return iw() - 2 * CONTENT_PAD; }
    private int thumbCols() { return Math.max(1, (innerWidth() + THUMB_GAP) / (THUMB + THUMB_GAP)); }
    private int contentBottom() { return paletteY + paletteH - DRAWER_PAD; }

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

        // The drawer scales with the calendar, so its open footprint + tab is folded
        // into the scale denominator. The calendar stays centred, so it's reserved
        // symmetrically (×2). Zero when no stamp packs are installed.
        int drawerReserve = StampCatalog.list().isEmpty()
                ? 0
                : (PALETTE_W - OPEN_OVERLAP + TAB_W + 4);

        // Uniform UI scale fitting panel + drawer reserve in the window. Capped at
        // 1.0 (shrink to fit small windows, never up-scale and blur).
        float scaleW = (width  - MAX_PANEL_W_MARGIN) / (float) (totalW + 2 * drawerReserve);
        float scaleH = (height - MAX_PANEL_H_MARGIN) / (float) totalH;
        uiScale = Math.min(1f, Math.min(scaleW, scaleH));
        uiScale = Math.max(MIN_UI_SCALE, uiScale);

        // Panel position is stored in VIRTUAL (pre-scale) coordinates. After
        // pose.scale(uiScale) maps those coords to screen pixels, the calendar
        // appears centred in the window. Solving (panelX_v * uiScale) = screenX
        // yields panelX_v = screenX / uiScale.
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
        if (drawerInteractive && isInPalettePanel(mx / uiScale, my / uiScale)) {
            paletteScroll = clampPaletteScroll(paletteScroll - (int) (dy * 16.0));
            return true;
        }
        if (maxScrollX > 0 && isMouseOverGrid(mx / uiScale, my / uiScale)) {
            scrollX = clampScroll(scrollX - dy * 16.0);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (drawerInteractive && isInPalettePanel(mx / uiScale, my / uiScale)) {
            paletteScroll = clampPaletteScroll(paletteScroll - (int) (delta * 16.0));
            return true;
        }
        if (maxScrollX > 0 && isMouseOverGrid(mx / uiScale, my / uiScale)) {
            scrollX = clampScroll(scrollX - delta * 16.0);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
    *///?}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // The whole UI is drawn scaled, so hit-test in virtual (pre-scale) space.
        double vmx = mx / uiScale;
        double vmy = my / uiScale;
        if (button == 1 && stampMode) {  // right-click clears the held/selected stamp
            if (selectedStampId != null) { deselectStamp(); return true; }
            if (selectedPaletteIndex >= 0) { selectedPaletteIndex = -1; return true; }
        }
        if (button == 0) {
            // Pull tab is always grabbable.
            if (tabW > 0 && !StampCatalog.list().isEmpty()
                    && vmx >= tabX && vmx < tabX + tabW && vmy >= tabY && vmy < tabY + tabH) {
                toggleStampMode();
                return true;
            }
            for (HitRect h : paletteHits) {
                if (h.contains(vmx, vmy)) { h.action.run(); return true; }
            }
            if (drawerInteractive) {
                // Caption field is managed manually (not a registered widget).
                if (captionBox != null && captionBox.visible && inBounds(captionBox, vmx, vmy)) {
                    captionBox.setFocused(true);
                    captionBox.mouseClicked(vmx, vmy, button);
                    return true;
                }
                if (captionBox != null) captionBox.setFocused(false);
                for (ThumbHit t : thumbHits) {
                    if (vmx >= t.x() && vmx < t.x() + t.w() && vmy >= t.y() && vmy < t.y() + t.h()) {
                        onPaletteThumbClicked(t.index());
                        return true;
                    }
                }
                if (isInPalettePanel(vmx, vmy)) return true; // swallow other in-drawer clicks
            }
            for (HitRect h : hits) {
                if (h.contains(vmx, vmy)) {
                    h.action.run();
                    return true;
                }
            }
            if (stampMode) {
                CalendarClientStore.Snapshot snap = CalendarClientStore.get();
                if (snap != null && isMouseOverGrid(vmx, vmy)) {
                    return handleGridStampClick(snap, vmx, vmy);
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && draggingId != null) {
            dragVX = mx / uiScale;
            dragVY = my / uiScale;
            if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) dragMoved = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingId != null) {
            UUID id = draggingId;
            draggingId = null;
            if (dragMoved) commitMove(id, mx / uiScale, my / uiScale);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Forward to the caption field manually (it isn't a registered widget).
        if (captionBox != null && captionBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitCaptionEdit();
                captionBox.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                captionBox.setFocused(false);  // unfocus rather than close the screen
                return true;
            }
            // canConsumeInput() swallows plain keys so the inventory key can't close mid-type.
            if (captionBox.keyPressed(key, scan, mods) || captionBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (captionBox != null && captionBox.isFocused() && captionBox.charTyped(c, mods)) {
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void removed() {
        commitCaptionEdit();
        super.removed();
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
        // No stamp packs -> no tab, force the drawer closed.
        boolean haveStamps = !StampCatalog.list().isEmpty();
        if (!haveStamps && stampMode) setStampMode(false);

        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        paletteHits.clear();
        thumbHits.clear();
        hoverDay = -1;
        hoverDow = -1;
        hoverStamp = null;
        hoverThumbIndex = -1;
        hoverVisibilityInfo = false;

        updateDrawerAnim();
        drawerEase = ease(drawerAnim);
        paletteX = Math.round(paletteXClosed + (paletteXOpen - paletteXClosed) * drawerEase);
        drawerInteractive = stampMode && drawerAnim > 0.999f;

        // Hit rects and hover live in virtual (pre-scale) space; remap the mouse.
        int vmx = Math.round(mouseX / uiScale);
        int vmy = Math.round(mouseY / uiScale);

        boolean bodyVisible = haveStamps && drawerAnim > 0.0015f;
        if (captionBox != null) captionBox.visible = bodyVisible;

        // Drawer shares the calendar's scaled pose so they shrink together.
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        // Drawer body first, so the calendar paints over its tucked portion.
        if (bodyVisible) renderPalette(g, vmx, vmy, partialTick);
        // Body is visual-only until settled; don't let a moving target eat clicks.
        if (!drawerInteractive) { paletteHits.clear(); thumbHits.clear(); }

        renderPlankFrame(g);
        renderMapBackground(g);

        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) {
            drawCenteredNoShadow(g, Component.translatable("gui.townstead_calendar.no_data"),
                    panelX + panelW / 2, panelY + panelH / 2 - 4, TEXT_DAY);
            if (haveStamps) renderDrawerTab(g, vmx, vmy);
            g.pose().popPose();
            return;
        }
        renderHeader(g, snap, vmx, vmy);
        renderTodayButton(g, snap, vmx, vmy);
        renderWeekdayLabels(g, snap);
        renderGrid(g, snap, vmx, vmy);
        renderStamps(g, snap, vmx, vmy);

        // Pull tab on top of everything so it's always grabbable.
        if (haveStamps) renderDrawerTab(g, vmx, vmy);

        g.pose().popPose();

        // Ghost of the to-be-placed stamp, riding the cursor (real coords).
        renderGhostCursor(g, mouseX, mouseY);

        // Tooltip renders OUTSIDE the scale block so the tooltip text stays
        // at native font size regardless of UI scale, and follows the real
        // mouse cursor position.
        renderHoverTooltip(g, snap, mouseX, mouseY);

        // Drawer tooltips last, so they sit above the calendar too.
        if (drawerInteractive && hoverThumbIndex >= 0) {
            List<StampCatalog.Entry> list = StampCatalog.list();
            if (hoverThumbIndex < list.size()) {
                g.renderTooltip(font, Component.literal(list.get(hoverThumbIndex).displayName()), mouseX, mouseY);
            }
        }
        if (drawerInteractive && hoverVisibilityInfo) {
            g.renderTooltip(font, font.split(
                    Component.translatable("gui.townstead_calendar.stamp.visibility.tooltip"), 180),
                    mouseX, mouseY);
        }
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
        nineSliceMap(g, panelX, panelY, panelW, panelH);
    }

    /** 9-slice the empty-map texture across an arbitrary rect (current pose space). */
    private void nineSliceMap(GuiGraphics g, int x, int y, int w, int h) {
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
        // A hovered stamp takes priority over the day cell beneath it.
        if (hoverStamp != null) {
            List<Component> lines = new ArrayList<>();
            String cap = hoverStamp.caption();
            if (cap != null && !cap.isBlank()) {
                lines.add(Component.literal(cap));
            } else {
                lines.add(Component.translatable("gui.townstead_calendar.stamp.no_caption")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (!StampCatalog.hasTexture(hoverStamp.textureId())) {
                String pack = hoverStamp.sourcePack();
                Component missing = (pack != null && !pack.isBlank())
                        ? Component.translatable("gui.townstead_calendar.stamp.missing_art_from", pack)
                        : Component.translatable("gui.townstead_calendar.stamp.missing_art");
                lines.add(missing.copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoverDay < 0) return;
        List<Component> lines = new ArrayList<>();
        // Full date: "Monday, January 4, 1234 A.D."
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

    /** Tiles a plank texture across a rect, clipped via {@link #enableVirtualScissor}. */
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

    /** Scissor in virtual coords: enableScissor ignores the pose, so scale to real px. */
    private void enableVirtualScissor(GuiGraphics g, int vx1, int vy1, int vx2, int vy2) {
        g.enableScissor(
                (int) Math.floor(vx1 * uiScale),
                (int) Math.floor(vy1 * uiScale),
                (int) Math.ceil(vx2 * uiScale),
                (int) Math.ceil(vy2 * uiScale));
    }

    /**
     * Drawer body, built like the calendar panel: plank-fill the whole rect (so
     * nothing shows through the parchment's torn edges), then the map-parchment
     * interior inset by {@link #DRAWER_PAD}, leaving a plank border.
     */
    private void renderDrawerPanel(GuiGraphics g) {
        int x = paletteX, y = paletteY, w = paletteW, h = paletteH;
        tilePlankScaled(g, FrameRenderer.PLANK_DARK, x, y, w, h);

        // Outer shadow.
        g.fill(x - 1, y - 1, x + w + 1, y,         FrameRenderer.FRAME_SHADOW);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(x - 1, y, x, y + h,                 FrameRenderer.FRAME_SHADOW);
        g.fill(x + w, y, x + w + 1, y + h,         FrameRenderer.FRAME_SHADOW);
        // Top/left bevel highlight.
        g.fill(x, y, x + w, y + 1, FrameRenderer.FRAME_HIGHLIGHT);
        g.fill(x, y, x + 1, y + h, FrameRenderer.FRAME_HIGHLIGHT);

        int t = DRAWER_PAD;
        nineSliceMap(g, x + t, y + t, w - 2 * t, h - 2 * t);
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

    // ── Stamp rendering ────────────────────────────────────────────────────

    /** Draw every stamp on the viewed month, clipped to the grid. */
    private void renderStamps(GuiGraphics g, CalendarClientStore.Snapshot snap, int vmx, int vmy) {
        List<CalendarStamp> all = CalendarStampClientStore.get();
        if (all.isEmpty()) return;
        int gridTop = contentY + HEADER_H + WEEKDAY_H;
        int gridBottom = contentY + contentH - FOOTER_H;
        boolean inGrid = vmy >= gridTop && vmy < gridBottom && vmx >= contentX && vmx < contentX + contentW;
        CalendarStamp hovered = inGrid ? topStampAt(snap, vmx, vmy) : null;
        hoverStamp = hovered;

        enableVirtualScissor(g, contentX, gridTop, contentX + contentW, gridBottom);
        for (CalendarStamp s : all) {
            if (s.year() != viewYear || s.monthIndex() != viewMonth) continue;
            int[] o = stampScreenOrigin(snap, s);
            if (o == null) continue;
            int sx = o[0], sy = o[1];
            if (s == hovered) drawStampGlow(g, sx, sy);
            drawStamp(g, s, sx, sy);
            if (s.id().equals(selectedStampId)) {
                drawRectBorder(g, sx - 1, sy - 1, STAMP_SIZE + 2, STAMP_SIZE + 2, SELECT_BORDER);
            }
        }
        g.disableScissor();
    }

    /** Translucent preview of the to-be-placed stamp on the cursor (place mode only). */
    private void renderGhostCursor(GuiGraphics g, int mouseX, int mouseY) {
        if (!stampMode || selectedStampId != null || draggingId != null) return;
        if (selectedPaletteIndex < 0) return;
        List<StampCatalog.Entry> list = StampCatalog.list();
        if (selectedPaletteIndex >= list.size()) return;
        if (isInPalettePanel(mouseX / uiScale, mouseY / uiScale)) return;
        if (!isMouseOverGrid(mouseX / uiScale, mouseY / uiScale)) return;

        int size = Math.max(8, Math.round(STAMP_SIZE * uiScale));
        int gx = mouseX - size / 2;
        int gy = mouseY - size / 2;
        ResourceLocation tex = list.get(selectedPaletteIndex).texture();
        g.setColor(1f, 1f, 1f, 0.75f);
        g.blit(tex, gx, gy, size, size, 0f, 0f, size, size, size, size);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Soft warm halo behind a hovered stamp (stacked translucent rings). */
    private void drawStampGlow(GuiGraphics g, int x, int y) {
        int s = STAMP_SIZE;
        g.fill(x - 3, y - 3, x + s + 3, y + s + 3, 0x18FFE680);
        g.fill(x - 2, y - 2, x + s + 2, y + s + 2, 0x28FFE680);
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, HOVER_GLOW);
    }

    private void drawStamp(GuiGraphics g, CalendarStamp s, int x, int y) {
        if (StampCatalog.hasTexture(s.textureId())) {
            // region == texture size -> UV 0..1, so any native resolution fits the square.
            ResourceLocation rl = StampCatalog.parse(s.textureId());
            g.blit(rl, x, y, STAMP_SIZE, STAMP_SIZE, 0f, 0f, STAMP_SIZE, STAMP_SIZE, STAMP_SIZE, STAMP_SIZE);
        } else {
            // Art not installed: a postmark seal with the stamp's initial (still hoverable).
            g.fill(x, y, x + STAMP_SIZE, y + STAMP_SIZE, CELL_FILL);
            drawRectBorder(g, x, y, STAMP_SIZE, STAMP_SIZE, CELL_BORDER);
            drawRectBorder(g, x + 2, y + 2, STAMP_SIZE - 4, STAMP_SIZE - 4, TODAY_BORDER);
            drawCenteredNoShadow(g, StampCatalog.shortLabel(s.textureId()),
                    x + STAMP_SIZE / 2, y + (STAMP_SIZE - font.lineHeight) / 2, TODAY_BORDER);
        }
    }

    // ── Drawer-pull tab + drawer ─────────────────────────────────────────────

    /** The pull tab; clicking it slides the drawer out (and back in). Virtual coords. */
    private void renderDrawerTab(GuiGraphics g, int mouseX, int mouseY) {
        int calRight  = panelX + panelW + WOOD_FRAME_THICKNESS;
        int calTop    = panelY - WOOD_FRAME_THICKNESS;
        int calBottom = panelY + panelH + WOOD_FRAME_THICKNESS;
        tabW = TAB_W;
        tabH = Math.min(TAB_H, calBottom - calTop);
        // Slides from just outside the frame (closed) to the open drawer's face (open).
        int tabClosedX = calRight + TAB_MARGIN;
        int tabOpenX   = paletteXOpen + paletteW;
        tabX = Math.round(tabClosedX + (tabOpenX - tabClosedX) * drawerEase);
        tabY = calTop + (calBottom - calTop - tabH) / 2;

        boolean hover = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + tabH;
        int bg = stampMode ? TODAY_FILL : (hover ? NAV_BG_HOVER : NAV_BG);
        int border = stampMode ? TODAY_BORDER : NAV_BORDER;

        g.fill(tabX + 1, tabY + 2, tabX + tabW + 1, tabY + tabH + 1, 0x50000000);  // drop shadow
        g.fill(tabX, tabY, tabX + tabW, tabY + tabH, bg);
        drawRectBorder(g, tabX, tabY, tabW, tabH, border);
        g.fill(tabX + tabW - 1, tabY + 1, tabX + tabW, tabY + tabH - 1, 0x30FFFFFF);  // edge highlight

        // Vertical "Stamps" label.
        String s = Component.translatable("gui.townstead_calendar.button.stamp").getString();
        g.pose().pushPose();
        g.pose().translate(tabX + tabW / 2f, tabY + tabH / 2f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90f));
        g.drawString(font, s, -font.width(s) / 2, -font.lineHeight / 2, TEXT_NAV, false);
        g.pose().popPose();
    }

    private void renderPalette(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        thumbHits.clear();
        hoverThumbIndex = -1;
        if (captionBox != null) captionBox.setX(innerLeft());  // re-anchor as the drawer slides

        renderDrawerPanel(g);

        int cl = innerLeft();
        int iwidth = innerWidth();
        List<StampCatalog.Entry> list = StampCatalog.list();
        if (list.isEmpty()) {
            drawCenteredNoShadow(g, Component.translatable("gui.townstead_calendar.stamp.no_packs"),
                    paletteX + paletteW / 2, drGridTop + 6, DRAWER_TEXT);
        } else {
            renderThumbnails(g, list, drGridTop, drGridBottom, mouseX, mouseY);
        }

        g.fill(cl, drDividerY, cl + iwidth, drDividerY + 1, CELL_BORDER);  // grid/compose divider

        g.drawString(font, Component.translatable("gui.townstead_calendar.stamp.caption"),
                cl, drCaptionLabelY, DRAWER_TEXT, false);
        if (captionBox != null) captionBox.render(g, mouseX, mouseY, partialTick);

        // Visibility switch; hovering the label shows its tooltip.
        Component visLabel = Component.translatable("gui.townstead_calendar.stamp.visibility");
        g.drawString(font, visLabel, cl, drVisLabelY, DRAWER_TEXT, false);
        if (mouseX >= cl && mouseX < cl + font.width(visLabel) && mouseY >= drVisLabelY && mouseY < drVisLabelY + LABEL_H) {
            hoverVisibilityInfo = true;
        }
        boolean pub = effectiveVisibilityPublic();
        int half = iwidth / 2;
        drawParchmentButton(g, cl, drVisToggleY, half, CTRL_H,
                Component.translatable("gui.townstead_calendar.stamp.private"), mouseX, mouseY, !pub);
        drawParchmentButton(g, cl + half, drVisToggleY, iwidth - half, CTRL_H,
                Component.translatable("gui.townstead_calendar.stamp.public"), mouseX, mouseY, pub);
        paletteHits.add(new HitRect(cl, drVisToggleY, half, CTRL_H, () -> setVisibility(false)));
        paletteHits.add(new HitRect(cl + half, drVisToggleY, iwidth - half, CTRL_H, () -> setVisibility(true)));

        // Delete button for the selected stamp (the icon/caption row was redundant).
        if (selectedStampId != null) {
            CalendarStamp sel = CalendarStampClientStore.byId(selectedStampId);
            boolean editable = sel != null && canEditLocal(sel);
            drawParchmentButton(g, cl, drDeleteY, iwidth, CTRL_H,
                    Component.translatable("gui.townstead_calendar.stamp.delete"), mouseX, mouseY, false);
            if (editable) paletteHits.add(new HitRect(cl, drDeleteY, iwidth, CTRL_H, this::deleteSelectedStamp));
        }
    }

    /** Parchment-toned button matching the calendar's nav/Today chrome. */
    private void drawParchmentButton(GuiGraphics g, int x, int y, int w, int h,
                                     Component label, int mouseX, int mouseY, boolean active) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = active ? TODAY_FILL : (hover ? NAV_BG_HOVER : NAV_BG);
        g.fill(x, y, x + w, y + h, bg);
        drawRectBorder(g, x, y, w, h, active ? TODAY_BORDER : NAV_BORDER);
        String s = label.getString();
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - font.lineHeight) / 2 + 1, TEXT_NAV, false);
    }

    private void renderThumbnails(GuiGraphics g, List<StampCatalog.Entry> list,
                                  int gridTop, int gridBottom, int mouseX, int mouseY) {
        int cols = thumbCols();
        int rows = (list.size() + cols - 1) / cols;
        int viewH = gridBottom - gridTop;
        paletteContentH = rows * (THUMB + THUMB_GAP);
        paletteScroll = clampPaletteScroll(paletteScroll);

        // Highlight the armed thumbnail (place mode) or the selected stamp's art (edit mode).
        int highlight = selectedPaletteIndex;
        if (selectedStampId != null) {
            CalendarStamp s = CalendarStampClientStore.byId(selectedStampId);
            highlight = (s != null) ? paletteIndexOf(s.textureId()) : -1;
        }

        int gridW = cols * THUMB + (cols - 1) * THUMB_GAP;
        int startX = innerLeft() + Math.max(0, (innerWidth() - gridW) / 2);  // centred
        // Scissor ignores the pose, so convert the virtual clip rect to real px.
        enableVirtualScissor(g, innerLeft(), gridTop, innerLeft() + innerWidth(), gridBottom);
        for (int i = 0; i < list.size(); i++) {
            int c = i % cols, r = i / cols;
            int tx = startX + c * (THUMB + THUMB_GAP);
            int ty = gridTop + r * (THUMB + THUMB_GAP) - paletteScroll;
            if (ty + THUMB <= gridTop || ty >= gridBottom) continue;
            boolean sel = (i == highlight);
            boolean hov = mouseX >= tx && mouseX < tx + THUMB
                    && mouseY >= Math.max(ty, gridTop) && mouseY < Math.min(ty + THUMB, gridBottom);
            int fill = sel ? TODAY_FILL : (hov ? CELL_HOVER : CELL_FILL);
            g.fill(tx, ty, tx + THUMB, ty + THUMB, fill);
            drawRectBorder(g, tx, ty, THUMB, THUMB, sel ? TODAY_BORDER : CELL_BORDER);
            int isz = THUMB - 10;
            int io = (THUMB - isz) / 2;
            g.blit(list.get(i).texture(), tx + io, ty + io, isz, isz, 0f, 0f, isz, isz, isz, isz);
            if (hov) hoverThumbIndex = i;
            thumbHits.add(new ThumbHit(tx, ty, THUMB, THUMB, i));
        }
        g.disableScissor();

        int maxScroll = Math.max(0, paletteContentH - viewH);
        if (maxScroll > 0) {
            int trackX = innerLeft() + innerWidth() + 2;  // sits in the right padding gutter
            int thumbH = Math.max(12, viewH * viewH / paletteContentH);
            int thumbY = gridTop + (viewH - thumbH) * paletteScroll / maxScroll;
            g.fill(trackX, gridTop, trackX + 2, gridBottom, 0x40000000);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8B6F47);
        }
    }

    private int clampPaletteScroll(int v) {
        int viewH = drGridBottom - drGridTop;
        int maxScroll = Math.max(0, paletteContentH - viewH);
        if (v < 0) return 0;
        if (v > maxScroll) return maxScroll;
        return v;
    }

    private boolean isInPalettePanel(double mx, double my) {
        return stampMode && mx >= paletteX && mx < paletteX + paletteW
                && my >= paletteY && my < paletteY + paletteH;
    }

    private static boolean inBounds(net.minecraft.client.gui.components.AbstractWidget w, double mx, double my) {
        return mx >= w.getX() && mx < w.getX() + w.getWidth()
                && my >= w.getY() && my < w.getY() + w.getHeight();
    }

    // ── Stamp interactions ──────────────────────────────────────────────────

    private void toggleStampMode() {
        setStampMode(!stampMode);
    }

    private void setStampMode(boolean on) {
        stampMode = on;
        if (on) {
            StampCatalog.refresh();
        } else {
            commitCaptionEdit();
            selectedStampId = null;
            selectedPaletteIndex = -1;
            draggingId = null;
            if (captionBox != null) {
                captionBox.setFocused(false);
                captionBox.setValue("");
            }
        }
        layoutRightPanel();
    }

    /** Visibility the switch reflects: the selected stamp's, else the new-stamp default. */
    private boolean effectiveVisibilityPublic() {
        if (selectedStampId != null) {
            CalendarStamp s = CalendarStampClientStore.byId(selectedStampId);
            if (s != null) return s.isPublic();
        }
        return newStampPublic;
    }

    private void setVisibility(boolean pub) {
        if (selectedStampId != null) {
            CalendarStamp s = CalendarStampClientStore.byId(selectedStampId);
            if (s != null && canEditLocal(s) && s.isPublic() != pub) {
                String cap = captionBox != null ? captionBox.getValue() : s.caption();
                // Empty texture keeps the current art; just set visibility.
                sendAction(CalendarStampActionC2SPayload.edit(selectedStampId, "", "", cap, pub));
            }
        } else {
            newStampPublic = pub;
        }
    }

    private void onPaletteThumbClicked(int index) {
        if (selectedStampId != null) {
            // Edit mode: re-art the selected stamp (does not arm placement).
            CalendarStamp s = CalendarStampClientStore.byId(selectedStampId);
            if (s == null || !canEditLocal(s)) return;
            List<StampCatalog.Entry> list = StampCatalog.list();
            if (index < 0 || index >= list.size()) return;
            String cap = captionBox != null ? captionBox.getValue() : s.caption();
            StampCatalog.Entry e = list.get(index);
            sendAction(CalendarStampActionC2SPayload.edit(selectedStampId, e.textureId(), e.sourcePack(), cap, s.isPublic()));
            return;
        }
        // Place mode: arm this thumbnail, or click it again to disarm.
        selectedPaletteIndex = (index == selectedPaletteIndex) ? -1 : index;
    }

    private boolean handleGridStampClick(CalendarClientStore.Snapshot snap, double vx, double vy) {
        CalendarStamp hit = topStampAt(snap, vx, vy);
        if (hit != null) {
            selectStamp(hit);
            if (canEditLocal(hit)) {
                int[] o = stampScreenOrigin(snap, hit);
                draggingId = hit.id();
                dragVX = vx;
                dragVY = vy;
                dragGrabDX = vx - o[0];
                dragGrabDY = vy - o[1];
                dragMoved = false;
            }
            return true;
        }
        if (selectedStampId != null) {
            deselectStamp();
            return true;
        }
        if (selectedPaletteIndex >= 0) {
            placeAt(snap, vx, vy);
        }
        return true;
    }

    private void placeAt(CalendarClientStore.Snapshot snap, double vx, double vy) {
        int day = dayAtPoint(snap, vx, vy);
        if (day < 0) return;
        List<StampCatalog.Entry> list = StampCatalog.list();
        if (selectedPaletteIndex < 0 || selectedPaletteIndex >= list.size()) return;
        int[] cell = cellOrigin(snap, day);
        if (cell == null) return;
        float offX = (float) (vx - cell[0] - STAMP_SIZE / 2.0);
        float offY = (float) (vy - cell[1] - STAMP_SIZE / 2.0);
        String caption = captionBox != null ? captionBox.getValue() : "";
        StampCatalog.Entry e = list.get(selectedPaletteIndex);
        sendAction(CalendarStampActionC2SPayload.place(
                e.textureId(), e.sourcePack(), caption, viewYear, viewMonth, day, offX, offY, newStampPublic));
    }

    private void commitMove(UUID id, double vx, double vy) {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) return;
        CalendarStamp s = CalendarStampClientStore.byId(id);
        if (s == null || !canEditLocal(s)) return;
        int newX = (int) Math.round(vx - dragGrabDX);
        int newY = (int) Math.round(vy - dragGrabDY);
        int day = dayAtPoint(snap, newX + STAMP_SIZE / 2.0, newY + STAMP_SIZE / 2.0);
        if (day < 0) return; // dropped off the grid; leave it where it was
        int[] cell = cellOrigin(snap, day);
        if (cell == null) return;
        sendAction(CalendarStampActionC2SPayload.move(
                id, viewYear, viewMonth, day, (float) (newX - cell[0]), (float) (newY - cell[1])));
    }

    private void selectStamp(CalendarStamp s) {
        commitCaptionEdit();
        selectedStampId = s.id();
        if (captionBox != null) captionBox.setValue(s.caption());
        selectedPaletteIndex = -1;  // edit mode, not placement: never arm the ghost
        layoutRightPanel();
    }

    private void deselectStamp() {
        commitCaptionEdit();
        selectedStampId = null;
        selectedPaletteIndex = -1;
        layoutRightPanel();
    }

    private void deleteSelectedStamp() {
        UUID id = selectedStampId;
        if (id == null) return;
        CalendarStamp s = CalendarStampClientStore.byId(id);
        if (s != null && canEditLocal(s)) sendAction(CalendarStampActionC2SPayload.remove(id));
        selectedStampId = null;
        layoutRightPanel();
    }

    private void commitCaptionEdit() {
        if (selectedStampId == null || captionBox == null) return;
        CalendarStamp s = CalendarStampClientStore.byId(selectedStampId);
        if (s == null || !canEditLocal(s)) return;
        String cap = captionBox.getValue();
        if (!cap.equals(s.caption())) {
            sendAction(CalendarStampActionC2SPayload.edit(selectedStampId, "", "", cap, s.isPublic()));
        }
    }

    private int paletteIndexOf(String textureId) {
        List<StampCatalog.Entry> list = StampCatalog.list();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).textureId().equals(textureId)) return i;
        }
        return -1;
    }

    private static boolean canEditLocal(CalendarStamp s) {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        return p.getUUID().equals(s.placedBy()) || p.hasPermissions(2);
    }

    private void sendAction(CalendarStampActionC2SPayload payload) {
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    // ── Grid geometry helpers ────────────────────────────────────────────────

    /** Screen origin (virtual coords) of a stamp, honouring an in-progress drag. */
    @Nullable
    private int[] stampScreenOrigin(CalendarClientStore.Snapshot snap, CalendarStamp s) {
        if (s.id().equals(draggingId)) {
            return new int[]{
                    (int) Math.round(dragVX - dragGrabDX),
                    (int) Math.round(dragVY - dragGrabDY)};
        }
        int[] cell = cellOrigin(snap, s.dayOfMonth());
        if (cell == null) return null;
        return new int[]{cell[0] + Math.round(s.offX()), cell[1] + Math.round(s.offY())};
    }

    /** Top-left (virtual coords) of the given 1-based day's cell, or null if out of range. */
    @Nullable
    private int[] cellOrigin(CalendarClientStore.Snapshot snap, int day) {
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> yearMonths = snap.monthsForYear(viewYear);
        if (yearMonths.isEmpty()) return null;
        int safeMonthIdx = Math.max(0, Math.min(viewMonth - 1, yearMonths.size() - 1));
        int monthDays = yearMonths.get(safeMonthIdx).days();
        if (day < 1 || day > monthDays) return null;
        int dpw = Math.max(1, snap.daysPerWeek());
        int startDow = startDayOfWeek(snap, viewYear, safeMonthIdx);
        int gridTop = contentY + HEADER_H + WEEKDAY_H;
        int cellIndex = startDow + day - 1;
        int col = cellIndex % dpw;
        int row = cellIndex / dpw;
        int cellX = contentX + col * (CELL_SIZE + CELL_GAP) - (int) scrollX;
        int cellY = gridTop + row * (CELL_SIZE + CELL_GAP);
        return new int[]{cellX, cellY};
    }

    /** Which 1-based day cell the given virtual point falls in, or -1 if none. */
    private int dayAtPoint(CalendarClientStore.Snapshot snap, double vx, double vy) {
        int gridTop = contentY + HEADER_H + WEEKDAY_H;
        int gridBottom = contentY + contentH - FOOTER_H;
        if (vx < contentX || vx >= contentX + contentW || vy < gridTop || vy >= gridBottom) return -1;
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> yearMonths = snap.monthsForYear(viewYear);
        if (yearMonths.isEmpty()) return -1;
        int safeMonthIdx = Math.max(0, Math.min(viewMonth - 1, yearMonths.size() - 1));
        int monthDays = yearMonths.get(safeMonthIdx).days();
        int dpw = Math.max(1, snap.daysPerWeek());
        int startDow = startDayOfWeek(snap, viewYear, safeMonthIdx);
        int relX = (int) Math.floor(vx - contentX + scrollX);
        int relY = (int) Math.floor(vy - gridTop);
        int col = relX / (CELL_SIZE + CELL_GAP);
        int row = relY / (CELL_SIZE + CELL_GAP);
        if (col < 0 || col >= dpw) return -1;
        int day = row * dpw + col - startDow + 1;
        if (day < 1 || day > monthDays) return -1;
        return day;
    }

    @Nullable
    private CalendarStamp topStampAt(CalendarClientStore.Snapshot snap, double vx, double vy) {
        CalendarStamp found = null;
        for (CalendarStamp s : CalendarStampClientStore.get()) {
            if (s.year() != viewYear || s.monthIndex() != viewMonth) continue;
            int[] o = stampScreenOrigin(snap, s);
            if (o == null) continue;
            if (vx >= o[0] && vx < o[0] + STAMP_SIZE && vy >= o[1] && vy < o[1] + STAMP_SIZE) {
                found = s; // last match wins -> topmost
            }
        }
        return found;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
