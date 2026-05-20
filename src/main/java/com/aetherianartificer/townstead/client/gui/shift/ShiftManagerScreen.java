package com.aetherianartificer.townstead.client.gui.shift;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.shift.ShiftWeekSetPayload;
import com.aetherianartificer.townstead.shift.template.Chronotype;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateApplyPayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateClientStore;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateDeletePayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateSavePayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlan;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanApplyPayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanClientStore;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanDeletePayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSavePayload;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ShiftManagerScreen extends Screen {

    private static final int EDGE = 18;
    private static final int HEADER_H = 28;
    private static final int CHECKBOX_SIZE = 11;
    private static final int NAME_W = 90;
    private static final int TEMPLATE_BTN_W = 134;
    private static final int CELL_H = 18;
    private static final int CELL_GAP = 2;
    private static final long HOURS_PER_DAY_TICKS = (long) ShiftData.HOURS_PER_DAY * ShiftData.TICKS_PER_HOUR;
    private static final int NOW_LINE_COLOR = 0xFFFFD040;
    private static final int OVERLAY_DIM = 0xC0000000;
    private static final int MODAL_BG = 0xFF1B1F26;
    private static final int MODAL_BORDER = 0xFF455565;
    private static final int LIST_SELECTED_BG = 0x60FFCC44;
    private static final int LIST_HOVER_BG = 0x40FFFFFF;
    private static final int LIST_ASSIGNED_TAG = 0xFFFFCC44;
    private static final int ROW_HOVER = 0x18FFFFFF;
    private static final int TEMPLATE_BTN_BG = 0xFF2A2F38;
    private static final int TEMPLATE_BTN_BORDER = 0xFF455565;
    private static final int TEMPLATE_BTN_BORDER_HI = 0xFF7A8FA8;
    private static final int CB_OUTLINE = 0xFFFFFFFF;
    private static final int CB_CHECK = 0xFFFFFFFF;
    private static final int SLEEP_BAND_COLOR = 0xB0E8E0C0;

    private final Screen returnScreen;

    // -- Tabs ---------------------------------------------------------------
    private static final int TAB_DAILY = 0;
    private static final int TAB_WEEKLY = 1;
    private static final int TAB_H = 18;
    private static final int TODAY_COL_TINT = 0x33FFD040;
    private static final int TODAY_COL_BORDER = 0xFFFFD040;
    private static final int WEEK_FALLBACK_FILL = 0x30FFFFFF;
    private int viewTab = TAB_DAILY;
    private int tabsY;
    private int dailyTabX, dailyTabW, weeklyTabX, weeklyTabW;

    // -- Weekly grid state --------------------------------------------------
    private int weekCols = 7;       // calendar daysPerWeek (fallback 7)
    private int weekCellW;
    private int weeklyFocusedDay = 0;
    private ShiftClientStore.WeekState weeklyWeekClipboard = null;   // copied whole week (row-level)
    private String weeklyClipboardName = null;                      // name of the villager it was copied from
    private final Set<UUID> weekQueried = new HashSet<>();
    private Button weeklyCopyButton, weeklyPasteButton;
    private Button weeklyApplyPlanButton, weeklySavePlanButton;
    // Responsive widths for the weekly toolbar clusters, computed in init() and
    // reused by the helper-text gap math so the two never disagree.
    private int weeklyEditW = 92, weeklyPlanW = 130;
    private static final int WEEKLY_TOOL_GAP = 6;
    private Button resetButton;

    // -- Day-assign modal (reuses the template modal in a different mode) ----
    private boolean modalDayAssign = false;
    private UUID modalDayTarget = null;
    private int modalDayIndex = -1;
    private Button modalAllDaysButton;

    // -- Week plan modal ----------------------------------------------------
    private boolean weekPlanModalActive = false;
    private boolean weekPlanBulk = false;
    private UUID weekPlanTarget = null;
    private List<UUID> weekPlanBulkTargets = List.of();
    private ResourceLocation weekPlanSelectedId = null;
    private int weekPlanListScroll = 0;
    private int weekPlanPreviewScroll = 0;   // scrolls the day-strip rows in the detail pane
    private boolean weekPlanSaveActive = false;
    private EditBox weekPlanSaveInput;
    private Button weekPlanApplyButton, weekPlanDeleteButton, weekPlanSaveButton, weekPlanCloseButton;
    private Button weekPlanSaveConfirm, weekPlanSaveCancel;
    private boolean weekPlanRenaming = false;
    private EditBox weekPlanRenameInput;
    private int wpTitleX, wpTitleY, wpTitleW, wpTitleH;

    private int nameLeft;
    private int gridLeft;
    private int gridRight;
    private int templateBtnLeft;
    private int cellW;
    // NAME_W / TEMPLATE_BTN_W are the wide-screen maximums; these hold the
    // actual widths after init() shrinks the name column and the right-side
    // control (template dropdown / mode toggle) to give the hour grid room on
    // narrow / high-GUI-scale windows.
    private int nameW = NAME_W;
    private int templateBtnW = TEMPLATE_BTN_W;
    private int gridTop;
    private int gridBottom;
    private int legendY;
    private int legendStep;

    private Button selectAllButton;
    private Button applyToSelectedButton;
    private EditBox searchBox;
    private String searchQuery = "";
    private int rowScroll = 0; // pixels

    private List<UUID> shiftVillagerUuids = List.of();
    private List<UUID> filteredUuids = List.of();
    private final Map<UUID, String> shiftVillagerNames = new HashMap<>();
    private final Map<UUID, Chronotype> shiftVillagerChronotypes = new HashMap<>();
    private final Map<UUID, String> shiftVillagerTemplateIds = new HashMap<>();
    private final Map<UUID, int[]> shiftEdits = new HashMap<>();
    private final Set<UUID> selectedVillagers = new LinkedHashSet<>();
    private UUID focusedVillager = null;
    private UUID lastToggledOn = null;
    private boolean shiftQueried = false;
    private int shiftPaintOrdinal = -1;

    // -- Modal state ---------------------------------------------------------
    private boolean modalActive = false;
    private boolean modalBulkMode = false;
    private UUID modalTarget = null;            // row that opened the modal (single-row mode)
    private List<UUID> modalBulkTargets = List.of();
    private ResourceLocation modalSelectedId = null;
    private int modalListScroll = 0;
    private int modalPreviewScroll = 0;   // scrolls the detail pane when content is taller than the pane
    private boolean modalSaveAsActive = false;
    private boolean modalRenamingTitle = false;
    private EditBox modalRenameInput;
    // Per-template local edits to user-template shifts (synced on each click)
    private final Map<ResourceLocation, int[]> modalTemplateEdits = new HashMap<>();
    // Last-known preview-area bounds; used by mouseClicked to hit-test the preview cells & title
    private int previewX, previewY, previewW, previewH;
    private int previewTitleX, previewTitleY, previewTitleW, previewTitleH;
    private int previewGridX, previewGridY, previewGridCellW, previewGridH;
    private final int[] summaryHitX = new int[ShiftData.ORDINAL_COLORS.length];
    private final int[] summaryHitW = new int[ShiftData.ORDINAL_COLORS.length];
    private final int[] summaryHitY = new int[ShiftData.ORDINAL_COLORS.length];
    private int summaryHitH = 0;
    private EditBox modalSaveAsInput;
    private Button modalLoadButton;
    private Button modalDuplicateButton;
    private Button modalDeleteButton;
    private Button modalSaveAsButton;
    private Button modalCloseButton;
    private Button modalSaveAsConfirmButton;
    private Button modalSaveAsCancelButton;
    // Shared delete-confirmation overlay (used by both the template and week
    // plan modals' Delete buttons, so deletes can't fire on a single misclick).
    private boolean confirmDeleteActive = false;
    private String confirmDeleteName = "";
    private Runnable confirmDeleteAction = null;
    private Button confirmDeleteYes;
    private Button confirmDeleteNo;

    public ShiftManagerScreen(Screen returnScreen) {
        super(Component.translatable("townstead.shift.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();

        nameLeft = EDGE + CHECKBOX_SIZE + 6;
        // Shrink the name column and the right-side control toward their minimums
        // as the window narrows, so the hour grid keeps enough width for legible
        // cells instead of the grid overflowing under the dropdown (the old
        // Math.max(8, ...) floor pushed 24 cells past gridRight on small screens).
        int span = width - 2 * EDGE;
        nameW = Math.max(56, Math.min(NAME_W, span * 18 / 100));
        templateBtnW = Math.max(88, Math.min(TEMPLATE_BTN_W, span * 26 / 100));
        gridLeft = nameLeft + nameW + 6;
        templateBtnLeft = width - EDGE - templateBtnW;
        gridRight = templateBtnLeft - 8;
        cellW = Math.max(4, (gridRight - gridLeft) / ShiftData.HOURS_PER_DAY);
        tabsY = EDGE + HEADER_H;
        gridTop = tabsY + TAB_H;

        weekCols = Math.max(1, currentDaysPerWeek());
        weekCellW = Math.max(8, (gridRight - gridLeft) / weekCols);

        int footerBtnY = height - EDGE - 20;
        legendY = footerBtnY - 18;

        gridBottom = legendY - 8;

        // Tabs: left-aligned folder tabs attached to the top of the grid panel.
        dailyTabW = 66;
        weeklyTabW = 66;
        dailyTabX = EDGE;
        weeklyTabX = dailyTabX + dailyTabW + 2;

        refreshShiftVillagers();
        pruneStateAgainstResidents();

        // Header
        int headerY = EDGE;
        selectAllButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.select_all"),
                b -> toggleSelectAllOnVisible())
                .bounds(EDGE, headerY, 56, 20)
                .build());

        applyToSelectedButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.apply_to_selected", 0),
                b -> openTemplateModalBulk())
                .bounds(EDGE + 60, headerY, 96, 20)
                .build());
        applyToSelectedButton.visible = false;

        int searchW = 100;
        searchBox = new EditBox(this.font, width - EDGE - searchW, headerY + 2, searchW, 16,
                Component.translatable("townstead.shift.search"));
        searchBox.setHint(Component.translatable("townstead.shift.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(v -> {
            searchQuery = v;
            rowScroll = 0;
            rebuildFilteredList();
        });
        addRenderableWidget(searchBox);

        // Footer
        addRenderableWidget(Button.builder(
                Component.translatable("townstead.gui.back"),
                b -> onClose())
                .bounds(EDGE, footerBtnY, 60, 20)
                .build());

        resetButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.reset"),
                b -> resetAllShifts())
                .bounds(width - EDGE - 70, footerBtnY, 70, 20)
                .build());

        legendStep = Math.max(56,
                (width - EDGE - 80 - (EDGE + 64)) / Math.max(1, ShiftData.ORDINAL_COLORS.length));

        // Weekly toolbar: two grouped clusters on the legend row. Left = quick
        // row-level edit (Copy/Paste week); right = Week Plans. Hidden in daily.
        int toolY = legendY - 2;
        int toolH = 16;
        int toolGap = WEEKLY_TOOL_GAP;
        // Two clusters pinned to the left and right edges. Cap each button at its
        // natural width on wide screens, but shrink both (keeping a center gap
        // for the clipboard hint) when the window is too narrow, so Paste week
        // and Save Plan can't collide.
        int centerGap = 16;
        int clusterMax = (width - 2 * EDGE - centerGap) / 2;
        int editW = Math.max(40, Math.min(92, (clusterMax - toolGap) / 2));
        int planW = Math.max(40, Math.min(130, (clusterMax - toolGap) / 2));
        weeklyEditW = editW;
        weeklyPlanW = planW;
        weeklyCopyButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.weekly.copy_day"), b -> weeklyCopyWeek())
                .bounds(EDGE, toolY, editW, toolH).build());
        weeklyPasteButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.weekly.paste_day"), b -> weeklyPasteWeek())
                .bounds(EDGE + editW + toolGap, toolY, editW, toolH).build());
        weeklyApplyPlanButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.weekplan.apply_short"), b -> openWeekPlanModalApply())
                .bounds(width - EDGE - planW, toolY, planW, toolH).build());
        weeklySavePlanButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.weekplan.save_short"), b -> openWeekPlanModalSave())
                .bounds(width - EDGE - planW * 2 - toolGap, toolY, planW, toolH).build());

        queryShiftData();
        applyTabVisibility();
    }

    // ---------------------------------------------------------------- Render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // 1.20.2+ (our 1.21 build) auto-dims the world behind a Screen inside
        // Screen.render; 1.20.1 does not, so the main screen would render over
        // the live world and be hard to read. Draw the dim ourselves on Forge.
        //? if forge {
        /*renderBackground(g);
        *///?}
        super.render(g, mouseX, mouseY, partialTicks);

        refreshShiftVillagers();
        pruneStateAgainstResidents();
        updateHeaderButtons();
        // Grid starts below the tab strip, plus a label band. Daily (hour row)
        // and weekly (weekday headers) use the SAME offset so the villager rows
        // sit at identical Y in both tabs and line up when toggling.
        gridTop = tabsY + TAB_H + 12;
        clampRowScroll();

        // Title
        g.drawCenteredString(this.font, getTitle(),
                width / 2, EDGE + 6, 0xFFFFFFFF);

        renderTabs(g, mouseX, mouseY);

        if (viewTab == TAB_DAILY) {
            renderDailyBody(g, mouseX, mouseY);
        } else {
            renderWeeklyBody(g, mouseX, mouseY);
            // Usage hint in the footer, to the right of the Back button.
            int footerY = height - EDGE - 20;
            g.drawString(this.font, Component.translatable("townstead.shift.weekly.hint"),
                    EDGE + 68, footerY + 6, 0xFF9098A2, false);
        }

        renderRowScrollbar(g);

        boolean anyModal = modalActive || weekPlanModalActive;

        // Tooltips suppressed while a modal is open (they'd stack on top of it)
        if (!anyModal) {
            if (viewTab == TAB_DAILY) renderHoverTooltips(g, mouseX, mouseY);
            else renderWeekHoverTooltips(g, mouseX, mouseY);
        }

        // Modal: translate to z=400 so it sits on top of the rest of the GUI,
        // the same trick vanilla uses for tooltip rendering. Higher z in MC's
        // GUI ortho projection is closer to the camera.
        if (modalActive) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400.0F);
            renderModal(g, mouseX, mouseY, partialTicks);
            g.pose().popPose();
        } else if (weekPlanModalActive) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400.0F);
            renderWeekPlanModal(g, mouseX, mouseY, partialTicks);
            g.pose().popPose();
        }
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int h = TAB_H - 2;
        int lineY = tabsY + h - 1;
        // Baseline that the tab row sits on; the active tab erases its segment so
        // it reads as connected to the content below (folder-tab look).
        g.fill(EDGE, lineY, width - EDGE, lineY + 1, 0xFF455565);
        drawTab(g, dailyTabX, dailyTabW, Component.translatable("townstead.shift.tab.daily"),
                viewTab == TAB_DAILY, mouseX, mouseY);
        drawTab(g, weeklyTabX, weeklyTabW, Component.translatable("townstead.shift.tab.weekly"),
                viewTab == TAB_WEEKLY, mouseX, mouseY);
    }

    private void drawTab(GuiGraphics g, int x, int w, Component label, boolean active, int mouseX, int mouseY) {
        int y = tabsY;
        int h = TAB_H - 2;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = active ? 0xFF2A2F38 : (hovered ? 0xFF242A33 : 0xFF1A1E25);
        g.fill(x, y, x + w, y + h, bg);
        int border = active ? 0xFFFFD040 : 0xFF455565;
        g.fill(x, y, x + w, y + 1, border);              // top
        g.fill(x, y, x + 1, y + h, border);              // left
        g.fill(x + w - 1, y, x + w, y + h, border);      // right
        if (active) {
            // erase the baseline under the active tab so it merges with content
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, bg);
        } else {
            g.fill(x, y + h - 1, x + w, y + h, 0xFF455565); // bottom for inactive
        }
        g.drawCenteredString(this.font, label, x + w / 2,
                y + (h - this.font.lineHeight) / 2 + 1, active ? 0xFFFFFFFF : 0xFFB0B0B0);
    }

    private void renderDailyBody(GuiGraphics g, int mouseX, int mouseY) {
        // When an activity is selected, hovering an hour header previews a
        // whole-column fill (click = set that hour for every villager).
        boolean overHourHeader = shiftPaintOrdinal >= 0
                && mouseY >= gridTop - 12 && mouseY < gridTop
                && mouseX >= gridLeft && mouseX < gridLeft + ShiftData.HOURS_PER_DAY * cellW;
        int hoverHour = overHourHeader ? (int) ((mouseX - gridLeft) / cellW) : -1;
        if (hoverHour >= 0) {
            int hx = gridLeft + hoverHour * cellW;
            g.fill(hx, gridTop - 12, hx + cellW - 1, gridTop - 1, 0x40FFFFFF);
        }

        // Hour labels in their own row, with breathing room above the cells.
        // Thin the ticks when cells are too narrow to fit a 2-digit number at
        // half scale, so they don't smear into each other; the hovered hour is
        // always labelled so you can still read the exact column.
        int hourLabelY = gridTop - 9;
        int hourStride = Math.max(1, (int) Math.ceil(7.0 / cellW));
        for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
            if (h % hourStride != 0 && h != hoverHour) continue;
            int displayHour = ShiftData.toDisplayHour(h);
            String label = String.valueOf(displayHour);
            int lx = gridLeft + h * cellW;
            g.pose().pushPose();
            g.pose().translate(lx + cellW / 2.0f, hourLabelY, 0);
            g.pose().scale(0.5f, 0.5f, 1.0f);
            int lc = (h == hoverHour) ? 0xFFFFFFFF : 0xFFC0C0C0;
            g.drawString(this.font, label, -this.font.width(label) / 2, 0, lc, false);
            g.pose().popPose();
        }

        // Scrollable row viewport — scissor-clip to the grid area
        g.enableScissor(EDGE - 2, gridTop, width - EDGE + 2, gridBottom);
        int visibleCount = 0;
        for (int idx = 0; idx < filteredUuids.size(); idx++) {
            int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < gridTop) continue;
            if (rowY > gridBottom) break;
            visibleCount++;
            UUID uuid = filteredUuids.get(idx);

            if (mouseY >= rowY && mouseY < rowY + CELL_H
                    && mouseY >= gridTop && mouseY < gridBottom) {
                g.fill(EDGE - 2, rowY - 1, width - EDGE + 2, rowY + CELL_H, ROW_HOVER);
            }

            renderCheckbox(g, EDGE, rowY + (CELL_H - CHECKBOX_SIZE) / 2, selectedVillagers.contains(uuid));
            renderName(g, uuid, rowY);
            renderGridRow(g, uuid, rowY, mouseX, mouseY);
            renderTemplateButton(g, uuid, rowY, mouseX, mouseY);
        }
        g.disableScissor();

        // Now-line — drawn last over cells so it's visible
        if (minecraft != null && minecraft.level != null && visibleCount > 0) {
            long dayTime = minecraft.level.getDayTime() % HOURS_PER_DAY_TICKS;
            int nowX = gridLeft + (int) ((dayTime * (long) cellW) / ShiftData.TICKS_PER_HOUR);
            g.fill(nowX, gridTop - 6, nowX + 1, gridBottom, NOW_LINE_COLOR);
            g.fill(nowX - 2, gridTop - 6, nowX + 3, gridTop - 4, NOW_LINE_COLOR);
            g.fill(nowX - 1, gridTop - 4, nowX + 2, gridTop - 3, NOW_LINE_COLOR);
        }

        // Legend (paint mode)
        for (int i = 0; i < ShiftData.ORDINAL_COLORS.length; i++) {
            int lx = EDGE + 70 + i * legendStep;
            boolean selected = shiftPaintOrdinal == i;
            if (selected) {
                g.fill(lx - 2, legendY - 2, lx + 40, legendY + 11, 0xFFFFFFFF);
                g.fill(lx - 1, legendY - 1, lx + 39, legendY + 10, 0xFF000000);
            }
            g.fill(lx, legendY, lx + 8, legendY + 8, ShiftData.ORDINAL_COLORS[i]);
            g.drawString(this.font, Component.translatable(ShiftData.ORDINAL_TO_KEY[i]),
                    lx + 10, legendY, selected ? 0xFFFFFFFF : 0xFFC0C0C0, false);
        }
    }

    private void renderName(GuiGraphics g, UUID uuid, int rowY) {
        String name = shiftVillagerNames.getOrDefault(uuid, "???");
        String truncated = name;
        while (this.font.width(truncated) > nameW - 2 && truncated.length() > 1) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        if (!truncated.equals(name)) truncated += "..";
        int color = uuid.equals(focusedVillager) ? 0xFFFFD040 : 0xFFFFFFFF;
        g.drawString(this.font, truncated, nameLeft,
                rowY + (CELL_H - this.font.lineHeight) / 2 + 1, color, false);
    }

    private void renderGridRow(GuiGraphics g, UUID uuid, int rowY, int mouseX, int mouseY) {
        int[] shifts = shiftEdits.containsKey(uuid) ? shiftEdits.get(uuid) : ShiftClientStore.get(uuid);
        Chronotype c = chronotypeOf(uuid);

        for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
            int cellX = gridLeft + h * cellW;
            int cellY = rowY;
            int ord = shifts[h];
            if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;
            g.fill(cellX, cellY, cellX + cellW - 1, cellY + CELL_H - 1, ShiftData.ORDINAL_COLORS[ord]);

            if (c.isPreferredSleepHour(h)) {
                g.fill(cellX, cellY, cellX + cellW - 1, cellY + 2, SLEEP_BAND_COLOR);
                g.fill(cellX, cellY + CELL_H - 3, cellX + cellW - 1, cellY + CELL_H - 1, SLEEP_BAND_COLOR);
            }

            if (mouseX >= cellX && mouseX < cellX + cellW - 1
                    && mouseY >= cellY && mouseY < cellY + CELL_H - 1) {
                g.fill(cellX, cellY, cellX + cellW - 1, cellY + CELL_H - 1, 0x40FFFFFF);
            }
        }
        // Subtle frame around the 24h strip (consistent with the weekly cells).
        drawCellBorder(g, gridLeft, rowY, ShiftData.HOURS_PER_DAY * cellW - 1, CELL_H - 1, 0x66000000);
    }

    private void renderTemplateButton(GuiGraphics g, UUID uuid, int rowY, int mouseX, int mouseY) {
        int btnX = templateBtnLeft;
        int btnY = rowY + (CELL_H - 14) / 2;
        int btnH = 14;
        boolean hovered = mouseX >= btnX && mouseX <= btnX + templateBtnW
                && mouseY >= btnY && mouseY <= btnY + btnH;

        // Frame
        g.fill(btnX, btnY, btnX + templateBtnW, btnY + btnH, TEMPLATE_BTN_BG);
        int border = hovered ? TEMPLATE_BTN_BORDER_HI : TEMPLATE_BTN_BORDER;
        g.fill(btnX, btnY, btnX + templateBtnW, btnY + 1, border);
        g.fill(btnX, btnY + btnH - 1, btnX + templateBtnW, btnY + btnH, border);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, border);
        g.fill(btnX + templateBtnW - 1, btnY, btnX + templateBtnW, btnY + btnH, border);

        String label = templateLabelFor(uuid);
        int maxLabelW = templateBtnW - 16;
        String trunc = label;
        while (this.font.width(trunc) > maxLabelW && trunc.length() > 1) {
            trunc = trunc.substring(0, trunc.length() - 1);
        }
        if (!trunc.equals(label)) trunc += "..";

        ShiftTemplate t = ShiftTemplateClientStore.find(shiftVillagerTemplateIds.get(uuid));
        int color = (t != null && t.builtIn()) ? 0xFFE0E0E0
                : (t != null ? 0xFFC9F0FF : 0xFFA0A0A0);
        g.drawString(this.font, trunc, btnX + 5,
                btnY + (btnH - this.font.lineHeight) / 2 + 1, color, false);

        // Chevron on the right
        int cvX = btnX + templateBtnW - 8;
        int cvY = btnY + btnH / 2 - 1;
        g.fill(cvX, cvY, cvX + 3, cvY + 1, 0xFFBBBBBB);
        g.fill(cvX + 1, cvY + 1, cvX + 2, cvY + 2, 0xFFBBBBBB);
    }

    private void renderCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int s = CHECKBOX_SIZE;
        // White outline, empty interior
        g.fill(x, y, x + s, y + 1, CB_OUTLINE);
        g.fill(x, y + s - 1, x + s, y + s, CB_OUTLINE);
        g.fill(x, y, x + 1, y + s, CB_OUTLINE);
        g.fill(x + s - 1, y, x + s, y + s, CB_OUTLINE);
        if (checked) {
            // Inset filled box with a gap between outline and fill
            g.fill(x + 3, y + 3, x + s - 3, y + s - 3, CB_CHECK);
        }
    }

    private void renderHoverTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseY < gridTop || mouseY > gridBottom) return;
        // Name tooltip
        for (int idx = 0; idx < filteredUuids.size(); idx++) {
            int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < gridTop) continue;
            if (rowY > gridBottom) break;
            if (mouseX >= nameLeft && mouseX < gridLeft - 4
                    && mouseY >= rowY && mouseY < rowY + CELL_H) {
                UUID uuid = filteredUuids.get(idx);
                VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(uuid);
                if (resident != null) {
                    String profName = profDisplayName(resident.professionId());
                    int level = resident.professionLevel();
                    String levelKey = "townstead.profession.level." + Math.min(Math.max(level, 1), 5);
                    String levelName = Component.translatable(levelKey).getString();
                    Chronotype c = chronotypeOf(uuid);
                    String chronoStr = Component.translatable(c.translationKey()).getString();
                    g.renderTooltip(this.font,
                            Component.literal(profName + " - " + levelName + " - " + chronoStr),
                            mouseX, mouseY);
                }
                return;
            }
        }

        // Cell tooltip
        int totalGridW = ShiftData.HOURS_PER_DAY * cellW;
        if (mouseX >= gridLeft && mouseX < gridLeft + totalGridW) {
            int h = (int) ((mouseX - gridLeft) / cellW);
            if (h >= 0 && h < ShiftData.HOURS_PER_DAY) {
                for (int idx = 0; idx < filteredUuids.size(); idx++) {
                    int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
                    if (rowY + CELL_H < gridTop) continue;
                    if (rowY > gridBottom) break;
                    if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                        UUID uuid = filteredUuids.get(idx);
                        int[] shifts = shiftEdits.containsKey(uuid) ? shiftEdits.get(uuid) : ShiftClientStore.get(uuid);
                        int displayHour = ShiftData.toDisplayHour(h);
                        String hourStr = ShiftData.formatHour(displayHour);
                        int ord = shifts[h];
                        if (ord < 0 || ord >= ShiftData.ORDINAL_TO_KEY.length) ord = ShiftData.ORD_IDLE;
                        String activityName = Component.translatable(ShiftData.ORDINAL_TO_KEY[ord]).getString();
                        String villagerName = shiftVillagerNames.getOrDefault(uuid, "???");
                        g.renderTooltip(this.font,
                                Component.literal(villagerName + " @ " + hourStr + ": " + activityName),
                                mouseX, mouseY);
                        return;
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------- Modal

    private void renderModal(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        g.fill(0, 0, width, height, OVERLAY_DIM);

        int mw = Math.min(560, width - 60);
        int mh = Math.min(360, height - 60);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;

        // Frame
        g.fill(mx, my, mx + mw, my + mh, MODAL_BG);
        drawBorder(g, mx, my, mw, mh, MODAL_BORDER);

        // Header
        String headerText;
        if (modalDayAssign) {
            headerText = Component.translatable("townstead.shift.weekly.assign_day",
                    weekdayLong(modalDayIndex)).getString();
        } else if (modalBulkMode) {
            headerText = Component.translatable("townstead.shift.template.title_bulk",
                    modalBulkTargets.size()).getString();
        } else if (modalTarget != null) {
            headerText = Component.translatable("townstead.shift.template.title_for",
                    shiftVillagerNames.getOrDefault(modalTarget, "???")).getString();
        } else {
            headerText = Component.translatable("townstead.shift.template.title").getString();
        }
        g.drawString(this.font, Component.literal(headerText), mx + 12, my + 10, 0xFFFFFFFF, false);

        // Layout — both panes share one height, ending just above the single
        // bottom button row, so the list and the preview line up 1:1.
        int listX = mx + 10;
        int listY = my + 30;
        int listW = (mw - 30) / 2;
        int rightX = listX + listW + 10;
        int rightW = mw - (rightX - mx) - 10;
        int rightY = listY;
        int btnRowY = my + mh - 10 - 20;
        int paneH = (btnRowY - 8) - listY;

        drawBorder(g, listX, listY, listW, paneH, MODAL_BORDER);
        drawBorder(g, rightX, rightY, rightW, paneH, MODAL_BORDER);

        renderModalList(g, listX, listY, listW, paneH, mouseX, mouseY);
        renderModalPreview(g, rightX, rightY, rightW, paneH, mouseX, mouseY, partialTicks);

        // Close button (X)
        if (modalCloseButton != null) modalCloseButton.render(g, mouseX, mouseY, partialTicks);
        if (modalLoadButton != null) modalLoadButton.render(g, mouseX, mouseY, partialTicks);
        if (modalDuplicateButton != null) modalDuplicateButton.render(g, mouseX, mouseY, partialTicks);
        if (modalDeleteButton != null) modalDeleteButton.render(g, mouseX, mouseY, partialTicks);
        if (modalSaveAsButton != null) modalSaveAsButton.render(g, mouseX, mouseY, partialTicks);
        if (modalAllDaysButton != null) modalAllDaysButton.render(g, mouseX, mouseY, partialTicks);

        if (modalSaveAsActive) {
            renderSaveAsOverlay(g, mouseX, mouseY, partialTicks);
        }
        if (confirmDeleteActive) {
            renderDeleteConfirm(g, mouseX, mouseY, partialTicks);
        }
    }

    private static final int LIST_ENTRY_H = 18;
    // Odd so the 1px divider splits the gap evenly (5px above / line / 5px below);
    // an even gap leaves one extra pixel above the line.
    private static final int LIST_DIVIDER_GAP = 11;

    // ---- Shared built-in/custom picker list (template + week-plan modals) ----
    // One implementation so both pickers get identical divider spacing, row
    // height, colours and hit-testing. Rows must be ordered built-ins first.

    /** One row in a picker list. */
    private record ListRow(String label, boolean builtIn, boolean selected, boolean assignedDot) {}

    private void renderEntryList(GuiGraphics g, int x, int y, int w, int h, int scroll,
                                 List<ListRow> rows, boolean reserveDot, int mouseX, int mouseY) {
        if (rows.isEmpty()) return;
        int innerX = x + 2;
        int innerY = y + 4;
        int innerR = x + w - 2;
        int innerB = y + h - 2;
        int textX = innerX + 6 + (reserveDot ? 8 : 0);
        // Clip to the box so entries scrolled past the edges don't draw outside.
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int dy = innerY - scroll;
        boolean sawBuiltIn = false;
        boolean dividerDrawn = false;
        for (ListRow row : rows) {
            if (sawBuiltIn && !row.builtIn() && !dividerDrawn) {
                int divY = dy + LIST_DIVIDER_GAP / 2;
                if (divY > innerY && divY < innerB) {
                    g.fill(innerX + 6, divY, innerR - 6, divY + 1, 0xFF455565);
                }
                dy += LIST_DIVIDER_GAP;
                dividerDrawn = true;
            }
            if (dy + LIST_ENTRY_H >= innerY && dy <= innerB) {
                int yT = Math.max(dy, innerY);
                int yB = Math.min(dy + LIST_ENTRY_H - 1, innerB);
                boolean hovered = mouseX >= innerX && mouseX < innerR && mouseY >= yT && mouseY <= yB;
                if (row.selected()) g.fill(innerX, yT, innerR, yB, LIST_SELECTED_BG);
                else if (hovered) g.fill(innerX, yT, innerR, yB, LIST_HOVER_BG);
                if (reserveDot && row.assignedDot()) {
                    int dotX = innerX + 6;
                    int dotY = dy + LIST_ENTRY_H / 2 - 2;
                    g.fill(dotX, dotY, dotX + 4, dotY + 4, LIST_ASSIGNED_TAG);
                }
                int color = row.builtIn() ? 0xFFE0E0E0 : 0xFFC9F0FF;
                String trunc = row.label();
                int maxW = (innerR - 6) - textX;
                while (this.font.width(trunc) > maxW && trunc.length() > 1) {
                    trunc = trunc.substring(0, trunc.length() - 1);
                }
                if (!trunc.equals(row.label())) trunc += "..";
                g.drawString(this.font, trunc, textX,
                        dy + (LIST_ENTRY_H - this.font.lineHeight) / 2 + 1, color, false);
            }
            dy += LIST_ENTRY_H;
            if (row.builtIn()) sawBuiltIn = true;
        }
        g.disableScissor();
    }

    /** Total content height (rows + the divider gap when both kinds are present). */
    private int entryListContentHeight(List<ListRow> rows) {
        int h = rows.size() * LIST_ENTRY_H;
        boolean hasBuilt = false, hasCustom = false;
        for (ListRow r : rows) { if (r.builtIn()) hasBuilt = true; else hasCustom = true; }
        if (hasBuilt && hasCustom) h += LIST_DIVIDER_GAP;
        return h;
    }

    /** Row index at mouseY (matching renderEntryList's layout), or -1. */
    private int entryListIndexAt(int y, int scroll, List<ListRow> rows, double mouseY) {
        int dy = (y + 4) - scroll;
        boolean sawBuiltIn = false;
        boolean dividerDone = false;
        for (int i = 0; i < rows.size(); i++) {
            ListRow row = rows.get(i);
            if (sawBuiltIn && !row.builtIn() && !dividerDone) { dy += LIST_DIVIDER_GAP; dividerDone = true; }
            if (mouseY >= dy && mouseY < dy + LIST_ENTRY_H) return i;
            dy += LIST_ENTRY_H;
            if (row.builtIn()) sawBuiltIn = true;
        }
        return -1;
    }

    private List<ListRow> templateRows() {
        String assignedId = modalTarget != null ? shiftVillagerTemplateIds.get(modalTarget) : null;
        List<ListRow> rows = new ArrayList<>();
        for (ShiftTemplate t : ShiftTemplateClientStore.all()) {
            rows.add(new ListRow(t.displayName(), t.builtIn(), t.id().equals(modalSelectedId),
                    t.id().toString().equals(assignedId)));
        }
        return rows;
    }

    private List<ListRow> weekPlanRows() {
        List<ListRow> rows = new ArrayList<>();
        for (WeekPlan p : WeekPlanClientStore.all()) {
            rows.add(new ListRow(p.displayName(), p.builtIn(), p.id().equals(weekPlanSelectedId), false));
        }
        return rows;
    }

    private void renderModalList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        renderEntryList(g, x, y, w, h, modalListScroll, templateRows(), true, mouseX, mouseY);
    }

    private void renderModalPreview(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTicks) {
        previewX = x; previewY = y; previewW = w; previewH = h;
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null) {
            String prompt = Component.translatable("townstead.shift.template.unassigned").getString();
            g.drawCenteredString(this.font, Component.literal(prompt), x + w / 2, y + h / 2 - 4, 0xFF808080);
            previewTitleW = 0;
            previewGridCellW = 0;
            return;
        }
        // Clip all preview content to the pane so nothing draws past the border.
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int padding = 10;
        int fh = this.font.lineHeight;
        boolean renamingThis = modalRenamingTitle && !t.builtIn() && modalRenameInput != null;
        boolean editable = !t.builtIn();

        int gridX = x + padding;
        int gridW = w - padding * 2;
        int pcell = Math.max(4, gridW / ShiftData.HOURS_PER_DAY);
        int gridActualW = pcell * ShiftData.HOURS_PER_DAY;
        int rightBound = gridX + gridActualW;

        int[] shifts = effectiveTemplateShifts(t);
        int[] counts = new int[ShiftData.ORDINAL_COLORS.length];
        for (int v : shifts) if (v >= 0 && v < counts.length) counts[v]++;

        // Measure the legend's wrapped line count up front so we know the total
        // content height (and therefore how far the pane can scroll).
        int swatchSize = 9;
        int legLineH = swatchSize + 6;
        int legendLines = 1;
        {
            int msx = gridX;
            for (int i = 0; i < counts.length; i++) {
                String label = Component.translatable(ShiftData.ORDINAL_TO_KEY[i]).getString() + " " + counts[i] + "h";
                int entryW = swatchSize + 3 + this.font.width(label);
                if (msx != gridX && msx + entryW > rightBound) { msx = gridX; legendLines++; }
                msx += entryW + 12;
            }
        }

        // Layout offsets from the content top (independent of scroll).
        int tagOff = fh + 3;
        int gridOff = tagOff + fh + 8;
        int legendOff = gridOff + CELL_H + 10;
        int usedOff = legendOff + legendLines * legLineH + 6;
        int contentH = usedOff + fh + (editable ? fh + 2 : 0);

        int viewH = h - padding * 2;
        int maxScroll = Math.max(0, contentH - viewH);
        modalPreviewScroll = Math.max(0, Math.min(modalPreviewScroll, maxScroll));
        int top = y + padding - modalPreviewScroll;

        // Title (clickable on user templates for inline rename) / EditBox when renaming
        int titleX = x + padding;
        int titleY = top;
        if (renamingThis) {
            modalRenameInput.setX(titleX);
            modalRenameInput.setY(titleY - 2);
            modalRenameInput.render(g, mouseX, mouseY, partialTicks);
            previewTitleX = titleX; previewTitleY = titleY;
            previewTitleW = modalRenameInput.getWidth();
            previewTitleH = modalRenameInput.getHeight();
        } else {
            g.drawString(this.font, Component.literal(t.displayName()), titleX, titleY, 0xFFFFFFFF, false);
            previewTitleX = titleX; previewTitleY = titleY;
            previewTitleW = this.font.width(t.displayName());
            previewTitleH = this.font.lineHeight;
        }

        // Type tag (hidden while renaming, since the EditBox overlaps it)
        if (!renamingThis) {
            String tag = t.builtIn() ? "Built-in" : "Custom";
            g.drawString(this.font, Component.literal(tag), titleX, top + tagOff, 0xFFA0A0A0, false);
        }

        // Preview grid — matches the outside grid (CELL_H, same cell colors)
        int gridY = top + gridOff;
        previewGridX = gridX;
        previewGridY = gridY;
        previewGridCellW = pcell;
        previewGridH = CELL_H;

        // Hour labels above (thinned so they don't smear when cells are narrow)
        int pStride = Math.max(1, (int) Math.ceil(7.0 / pcell));
        for (int h2 = 0; h2 < ShiftData.HOURS_PER_DAY; h2 += pStride) {
            int displayHour = ShiftData.toDisplayHour(h2);
            String label = String.valueOf(displayHour);
            int lx = gridX + h2 * pcell;
            g.pose().pushPose();
            g.pose().translate(lx + pcell / 2.0f, gridY - 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(this.font, label, -this.font.width(label) / 2, -this.font.lineHeight, 0xFFC0C0C0, false);
            g.pose().popPose();
        }
        for (int h2 = 0; h2 < ShiftData.HOURS_PER_DAY; h2++) {
            int cx = gridX + h2 * pcell;
            int ord = shifts[h2];
            if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;
            g.fill(cx, gridY, cx + pcell - 1, gridY + CELL_H - 1, ShiftData.ORDINAL_COLORS[ord]);
            if (editable && mouseX >= cx && mouseX < cx + pcell - 1
                    && mouseY >= gridY && mouseY < gridY + CELL_H - 1) {
                g.fill(cx, gridY, cx + pcell - 1, gridY + CELL_H - 1, 0x40FFFFFF);
            }
        }

        // Activity summary legend (clickable to set paint mode), wraps as needed.
        int textOffsetY = swatchSize - this.font.lineHeight + 1;
        summaryHitH = swatchSize + 4;
        int sx = gridX;
        int sy = top + legendOff;
        for (int i = 0; i < counts.length; i++) {
            String label = Component.translatable(ShiftData.ORDINAL_TO_KEY[i]).getString() + " " + counts[i] + "h";
            int entryW = swatchSize + 3 + this.font.width(label);
            if (sx != gridX && sx + entryW > rightBound) { sx = gridX; sy += legLineH; }
            int hitY = sy - 2;
            boolean active = shiftPaintOrdinal == i;
            if (active) {
                g.fill(sx - 2, hitY, sx + entryW + 2, hitY + summaryHitH, 0xFFFFFFFF);
                g.fill(sx - 1, hitY + 1, sx + entryW + 1, hitY + summaryHitH - 1, 0xFF000000);
            }
            g.fill(sx, sy, sx + swatchSize, sy + swatchSize, ShiftData.ORDINAL_COLORS[i]);
            g.drawString(this.font, label, sx + swatchSize + 3, sy + textOffsetY,
                    active ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            summaryHitX[i] = sx;
            summaryHitW[i] = entryW;
            summaryHitY[i] = hitY;
            sx += entryW + 12;
        }

        // Assigned-to count + editable hint, flowing below the legend.
        int used = countAssignedTo(t);
        String usedText = used == 0
                ? Component.translatable("townstead.shift.template.used_none").getString()
                : Component.translatable("townstead.shift.template.used_by", used).getString();
        g.drawString(this.font, usedText, gridX, top + usedOff, 0xFFA0A0A0, false);
        if (editable) {
            String hint = Component.translatable("townstead.shift.template.edit_hint").getString();
            g.drawString(this.font, hint, gridX, top + usedOff + fh + 2, 0xFF707070, false);
        }
        g.disableScissor();

        // Scrollbar thumb when the content is taller than the pane.
        if (maxScroll > 0) {
            int trackX = x + w - 3;
            int thumbH = Math.max(12, viewH * viewH / contentH);
            int thumbY = (y + padding) + (viewH - thumbH) * modalPreviewScroll / maxScroll;
            g.fill(trackX, y + padding, trackX + 2, y + h - padding, 0x33FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    private int[] effectiveTemplateShifts(ShiftTemplate t) {
        int[] edited = modalTemplateEdits.get(t.id());
        if (edited != null) return edited;
        return t.copyShifts();
    }

    private int countAssignedTo(ShiftTemplate t) {
        String idStr = t.id().toString();
        int n = 0;
        for (String assigned : shiftVillagerTemplateIds.values()) {
            if (idStr.equals(assigned)) n++;
        }
        return n;
    }

    private void renderSaveAsOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // Lift above the modal content so the opaque panel occludes the
        // (text-batched) preview behind it instead of letting it bleed through.
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        g.fill(0, 0, width, height, OVERLAY_DIM);
        int mw = 280;
        int mh = 88;
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        g.fill(mx, my, mx + mw, my + mh, MODAL_BG);
        drawBorder(g, mx, my, mw, mh, MODAL_BORDER);

        g.drawString(this.font, Component.translatable("townstead.shift.template.save_prompt"),
                mx + 12, my + 10, 0xFFFFFFFF, false);
        if (modalSaveAsInput != null) modalSaveAsInput.render(g, mouseX, mouseY, partialTicks);
        if (modalSaveAsConfirmButton != null) modalSaveAsConfirmButton.render(g, mouseX, mouseY, partialTicks);
        if (modalSaveAsCancelButton != null) modalSaveAsCancelButton.render(g, mouseX, mouseY, partialTicks);
        g.pose().popPose();
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // -------------------------------------------------- Delete confirmation
    // A small overlay rendered on top of whichever modal is open (the same
    // sub-panel pattern as the Save As overlay), not a separate Screen.

    private static final int CONFIRM_W = 300;
    private static final int CONFIRM_H = 96;

    private void openDeleteConfirm(String name, Runnable onConfirm) {
        confirmDeleteActive = true;
        confirmDeleteName = name != null ? name : "";
        confirmDeleteAction = onConfirm;
        int mx = (width - CONFIRM_W) / 2;
        int my = (height - CONFIRM_H) / 2;
        int bw = 96;
        int by = my + CONFIRM_H - 28;
        confirmDeleteYes = Button.builder(Component.translatable("townstead.shift.confirm.delete_yes"), b -> {
            Runnable r = confirmDeleteAction;
            closeDeleteConfirm();
            if (r != null) r.run();
        }).bounds(mx + CONFIRM_W - bw - 12, by, bw, 20).build();
        confirmDeleteNo = Button.builder(Component.translatable("townstead.shift.confirm.cancel"),
                b -> closeDeleteConfirm())
                .bounds(mx + CONFIRM_W - 2 * bw - 18, by, bw, 20).build();
    }

    private void closeDeleteConfirm() {
        confirmDeleteActive = false;
        confirmDeleteAction = null;
        confirmDeleteName = "";
        confirmDeleteYes = null;
        confirmDeleteNo = null;
    }

    private void renderDeleteConfirm(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // Lift above the modal content so nothing behind bleeds through.
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        g.fill(0, 0, width, height, OVERLAY_DIM);
        int mx = (width - CONFIRM_W) / 2;
        int my = (height - CONFIRM_H) / 2;
        g.fill(mx, my, mx + CONFIRM_W, my + CONFIRM_H, MODAL_BG);
        drawBorder(g, mx, my, CONFIRM_W, CONFIRM_H, MODAL_BORDER);

        g.drawString(this.font, Component.translatable("townstead.shift.confirm.delete_title"),
                mx + 12, my + 12, 0xFFFFFFFF, false);
        Component msg = Component.translatable("townstead.shift.confirm.delete_msg", confirmDeleteName);
        int ty = my + 28;
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(msg, CONFIRM_W - 24)) {
            g.drawString(this.font, line, mx + 12, ty, 0xFFC0C0C0, false);
            ty += this.font.lineHeight + 1;
        }
        if (confirmDeleteNo != null) confirmDeleteNo.render(g, mouseX, mouseY, partialTicks);
        if (confirmDeleteYes != null) confirmDeleteYes.render(g, mouseX, mouseY, partialTicks);
        g.pose().popPose();
    }

    // --------------------------------------------------------- Modal control

    private void openTemplateModal(UUID target) {
        modalActive = true;
        modalBulkMode = false;
        modalTarget = target;
        modalBulkTargets = List.of();
        modalSaveAsActive = false;
        modalListScroll = 0;
        modalPreviewScroll = 0;
        // Default selection: the currently-assigned template for that row, if any
        String assigned = target != null ? shiftVillagerTemplateIds.get(target) : null;
        ShiftTemplate assignedTemplate = ShiftTemplateClientStore.find(assigned);
        modalSelectedId = assignedTemplate != null ? assignedTemplate.id() : null;

        rebuildModalWidgets();
    }

    private void openTemplateModalBulk() {
        if (selectedVillagers.isEmpty()) return;
        modalActive = true;
        modalBulkMode = true;
        modalTarget = null;
        modalBulkTargets = new ArrayList<>(selectedVillagers);
        modalSaveAsActive = false;
        modalListScroll = 0;
        modalPreviewScroll = 0;
        modalSelectedId = null;
        rebuildModalWidgets();
    }

    private void rebuildModalWidgets() {
        // Tear down any existing modal widgets first
        clearModalWidgets();

        int mw = Math.min(560, width - 60);
        int mh = Math.min(360, height - 60);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;

        // Single button row across the full content width at the bottom. The
        // set of buttons depends on the mode; they're spread evenly so the panes
        // above can be full height and the actions all line up on one line.
        int barX = mx + 10;
        int barW = mw - 20;
        int gap = 6;
        int btnRowH = 20;
        int btnRowY = my + mh - 10 - btnRowH;

        if (modalDayAssign) {
            // Fill all days | Load (assign to this day) | Delete
            int bw = (barW - 2 * gap) / 3;
            modalAllDaysButton = Button.builder(
                    Component.translatable("townstead.shift.weekly.fill_all"),
                    b -> applyDayTemplateAllDays())
                    .bounds(barX, btnRowY, bw, btnRowH).build();
            modalLoadButton = Button.builder(
                    Component.translatable("townstead.shift.template.load"),
                    b -> applySelectedTemplate())
                    .bounds(barX + bw + gap, btnRowY, bw, btnRowH).build();
            modalDeleteButton = Button.builder(
                    Component.translatable("townstead.shift.template.delete"),
                    b -> requestDeleteTemplate())
                    .bounds(barX + 2 * (bw + gap), btnRowY, barW - 2 * (bw + gap), btnRowH).build();
            modalDuplicateButton = null;
            modalSaveAsButton = null;
        } else if (modalBulkMode) {
            // Load | Delete
            int bw = (barW - gap) / 2;
            modalLoadButton = Button.builder(
                    Component.translatable("townstead.shift.template.load"),
                    b -> applySelectedTemplate())
                    .bounds(barX, btnRowY, bw, btnRowH).build();
            modalDeleteButton = Button.builder(
                    Component.translatable("townstead.shift.template.delete"),
                    b -> requestDeleteTemplate())
                    .bounds(barX + bw + gap, btnRowY, barW - bw - gap, btnRowH).build();
            modalDuplicateButton = null;
            modalSaveAsButton = null;
            modalAllDaysButton = null;
        } else {
            // Duplicate | Save As | Load | Delete
            int bw = (barW - 3 * gap) / 4;
            modalDuplicateButton = Button.builder(
                    Component.translatable("townstead.shift.template.duplicate"),
                    b -> duplicateSelectedTemplate())
                    .bounds(barX, btnRowY, bw, btnRowH).build();
            modalSaveAsButton = Button.builder(
                    Component.translatable("townstead.shift.template.save"),
                    b -> openSaveAsOverlay())
                    .bounds(barX + (bw + gap), btnRowY, bw, btnRowH).build();
            modalLoadButton = Button.builder(
                    Component.translatable("townstead.shift.template.load"),
                    b -> applySelectedTemplate())
                    .bounds(barX + 2 * (bw + gap), btnRowY, bw, btnRowH).build();
            modalDeleteButton = Button.builder(
                    Component.translatable("townstead.shift.template.delete"),
                    b -> requestDeleteTemplate())
                    .bounds(barX + 3 * (bw + gap), btnRowY, barW - 3 * (bw + gap), btnRowH).build();
            modalAllDaysButton = null;
        }
        modalCloseButton = Button.builder(
                Component.translatable("townstead.shift.template.close"),
                b -> closeModal())
                .bounds(mx + mw - 60, my + 6, 50, 18)
                .build();

        updateModalActionStates();
    }

    private void updateModalActionStates() {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (modalLoadButton != null) modalLoadButton.active = t != null && hasApplyTarget();
        if (modalDuplicateButton != null) modalDuplicateButton.active = t != null && modalTarget != null;
        if (modalDeleteButton != null) modalDeleteButton.active = t != null && !t.builtIn();
        if (modalSaveAsButton != null) modalSaveAsButton.active = modalTarget != null;
        if (modalAllDaysButton != null) modalAllDaysButton.active = t != null && modalDayTarget != null;
    }

    private boolean hasApplyTarget() {
        if (modalDayAssign) return modalDayTarget != null;
        return modalBulkMode ? !modalBulkTargets.isEmpty() : modalTarget != null;
    }

    private void clearModalWidgets() {
        modalLoadButton = null;
        modalDuplicateButton = null;
        modalDeleteButton = null;
        modalSaveAsButton = null;
        modalAllDaysButton = null;
        modalCloseButton = null;
        modalSaveAsInput = null;
        modalSaveAsConfirmButton = null;
        modalSaveAsCancelButton = null;
    }

    private void closeModal() {
        if (modalRenamingTitle) cancelRename();
        modalActive = false;
        modalBulkMode = false;
        modalTarget = null;
        modalBulkTargets = List.of();
        modalSelectedId = null;
        modalSaveAsActive = false;
        modalDayAssign = false;
        modalDayTarget = null;
        modalDayIndex = -1;
        modalTemplateEdits.clear();
        closeDeleteConfirm();
        clearModalWidgets();
        setFocused(null);
    }

    // -- Inline rename ------------------------------------------------------

    private void startRename(ShiftTemplate t) {
        if (t == null || t.builtIn()) return;
        modalRenamingTitle = true;
        int titleW = Math.max(120, previewW - 40);
        modalRenameInput = new EditBox(this.font, previewTitleX, previewTitleY - 2, titleW, 14,
                Component.translatable("townstead.shift.template.save_prompt"));
        modalRenameInput.setMaxLength(64);
        modalRenameInput.setValue(t.displayName());
        //? if >=1.21 {
        modalRenameInput.moveCursorToEnd(false);
        //?} else {
        /*modalRenameInput.moveCursorToEnd();
        *///?}
        modalRenameInput.setHighlightPos(0);
        modalRenameInput.setFocused(true);
        setFocused(modalRenameInput);
    }

    private void commitRename() {
        if (!modalRenamingTitle || modalRenameInput == null) return;
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null || t.builtIn()) { cancelRename(); return; }
        String name = modalRenameInput.getValue().trim();
        if (name.isEmpty() || name.equals(t.displayName())) { cancelRename(); return; }
        // Upsert by id; the server keeps the same id and replaces the name.
        java.util.Optional<String> chrono = t.chronotype().map(Enum::name);
        ShiftTemplateSavePayload payload = new ShiftTemplateSavePayload(
                t.id().toString(), name, effectiveTemplateShifts(t),
                chrono.isPresent() ? chrono : java.util.Optional.empty());
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        cancelRename();
    }

    private void cancelRename() {
        modalRenamingTitle = false;
        modalRenameInput = null;
        setFocused(null);
    }

    // -- Inline edit of user template ---------------------------------------

    private boolean paintPreviewCell(double mouseX, double mouseY, ShiftTemplate t) {
        if (t == null || t.builtIn() || previewGridCellW <= 0) return false;
        if (mouseX < previewGridX || mouseX >= previewGridX + previewGridCellW * ShiftData.HOURS_PER_DAY) return false;
        if (mouseY < previewGridY || mouseY >= previewGridY + previewGridH) return false;
        int h = (int) ((mouseX - previewGridX) / previewGridCellW);
        if (h < 0 || h >= ShiftData.HOURS_PER_DAY) return false;

        int[] base = effectiveTemplateShifts(t);
        int[] next = Arrays.copyOf(base, base.length);
        if (shiftPaintOrdinal >= 0) {
            if (next[h] == shiftPaintOrdinal) return true;
            next[h] = shiftPaintOrdinal;
        } else {
            next[h] = (next[h] + 1) % ShiftData.ORDINAL_TO_ACTIVITY.length;
        }
        modalTemplateEdits.put(t.id(), next);
        // Send save: same id, same name, new shifts; server upserts in place.
        java.util.Optional<String> chrono = t.chronotype().map(Enum::name);
        ShiftTemplateSavePayload payload = new ShiftTemplateSavePayload(
                t.id().toString(), t.displayName(), next,
                chrono.isPresent() ? chrono : java.util.Optional.empty());
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        return true;
    }

    private void openSaveAsOverlay() {
        if (modalTarget == null) return;
        modalSaveAsActive = true;
        int mw = 280;
        int mh = 88;
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        modalSaveAsInput = new EditBox(this.font, mx + 12, my + 24, mw - 24, 18,
                Component.translatable("townstead.shift.template.save_prompt"));
        modalSaveAsInput.setMaxLength(64);
        String defaultName = "Custom: " + shiftVillagerNames.getOrDefault(modalTarget, "Villager");
        modalSaveAsInput.setValue(defaultName);
        modalSaveAsInput.setFocused(true);
        setFocused(modalSaveAsInput);
        modalSaveAsConfirmButton = Button.builder(
                Component.translatable("townstead.shift.template.ok"),
                b -> confirmSaveAs())
                .bounds(mx + mw - 130, my + mh - 26, 60, 20)
                .build();
        modalSaveAsCancelButton = Button.builder(
                Component.translatable("townstead.shift.template.cancel"),
                b -> { modalSaveAsActive = false; modalSaveAsInput = null; setFocused(null); })
                .bounds(mx + mw - 66, my + mh - 26, 60, 20)
                .build();
    }

    private void confirmSaveAs() {
        if (modalSaveAsInput == null || modalTarget == null) {
            modalSaveAsActive = false;
            return;
        }
        String name = modalSaveAsInput.getValue().trim();
        if (name.isEmpty()) name = "Untitled";
        int[] shifts = shiftEdits.containsKey(modalTarget)
                ? shiftEdits.get(modalTarget)
                : ShiftClientStore.get(modalTarget);
        if (shifts == null || shifts.length != ShiftData.HOURS_PER_DAY) {
            modalSaveAsActive = false;
            return;
        }
        Optional<String> chronoName = Optional.of(chronotypeOf(modalTarget).name());
        ShiftTemplateSavePayload payload =
                new ShiftTemplateSavePayload("", name, Arrays.copyOf(shifts, shifts.length), chronoName);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        modalSaveAsActive = false;
        modalSaveAsInput = null;
        setFocused(null);
    }

    private void applySelectedTemplate() {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null) return;
        if (modalDayAssign) {
            if (modalDayTarget != null && modalDayIndex >= 0) {
                assignDayTemplate(modalDayTarget, modalDayIndex, t.id().toString());
            }
            closeModal();
            return;
        }
        List<UUID> targets;
        if (modalBulkMode) targets = new ArrayList<>(modalBulkTargets);
        else if (modalTarget != null) targets = new ArrayList<>(List.of(modalTarget));
        else return;
        if (targets.isEmpty()) return;

        int[] shifts = effectiveTemplateShifts(t);
        for (UUID uuid : targets) {
            ShiftClientStore.set(uuid, shifts);
            VillageResidentClientStore.updateShifts(uuid, shifts);
            shiftEdits.remove(uuid);
            shiftVillagerTemplateIds.put(uuid, t.id().toString());
        }
        ShiftTemplateApplyPayload payload = new ShiftTemplateApplyPayload(t.id(), targets);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        closeModal();
    }

    private void duplicateSelectedTemplate() {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null || modalTarget == null) return;
        String name = t.displayName() + " (copy)";
        Optional<String> chrono = t.chronotype().map(Enum::name);
        ShiftTemplateSavePayload payload =
                new ShiftTemplateSavePayload("", name, t.copyShifts(), chrono);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    /** Ask for confirmation before deleting the selected (user) template. */
    private void requestDeleteTemplate() {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null || t.builtIn()) return;
        openDeleteConfirm(t.displayName(), this::deleteSelectedTemplate);
    }

    private void deleteSelectedTemplate() {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null || t.builtIn()) return;
        ShiftTemplateDeletePayload payload = new ShiftTemplateDeletePayload(t.id());
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        modalSelectedId = null;
    }

    // ----------------------------------------------------------------- Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modalActive) return modalMouseClicked(mouseX, mouseY, button);
        if (weekPlanModalActive) return weekPlanModalMouseClicked(mouseX, mouseY, button);

        // Tab strip
        if (button == 0 && mouseY >= tabsY && mouseY <= tabsY + TAB_H - 2) {
            if (mouseX >= dailyTabX && mouseX <= dailyTabX + dailyTabW) { switchTab(TAB_DAILY); return true; }
            if (mouseX >= weeklyTabX && mouseX <= weeklyTabX + weeklyTabW) { switchTab(TAB_WEEKLY); return true; }
        }

        if (viewTab == TAB_WEEKLY) {
            if (weeklyMouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            // Hour-label header: with an activity selected, clicking an hour
            // column sets that hour for ALL villagers in the list.
            if (shiftPaintOrdinal >= 0 && mouseY >= gridTop - 12 && mouseY < gridTop
                    && mouseX >= gridLeft && mouseX < gridLeft + ShiftData.HOURS_PER_DAY * cellW) {
                int h = (int) ((mouseX - gridLeft) / cellW);
                fillHourForAll(h, shiftPaintOrdinal);
                return true;
            }

            // Clicks outside the grid viewport ignore row hit-testing
            boolean inViewport = mouseY >= gridTop && mouseY <= gridBottom;

            // Checkbox
            if (inViewport) for (int idx = 0; idx < filteredUuids.size(); idx++) {
                int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
                if (rowY + CELL_H < gridTop) continue;
                if (rowY > gridBottom) break;
                int cbY = rowY + (CELL_H - CHECKBOX_SIZE) / 2;
                if (mouseX >= EDGE && mouseX <= EDGE + CHECKBOX_SIZE
                        && mouseY >= cbY && mouseY <= cbY + CHECKBOX_SIZE) {
                    UUID uuid = filteredUuids.get(idx);
                    if (hasShiftDown() && lastToggledOn != null) {
                        rangeSelect(lastToggledOn, uuid);
                    } else if (selectedVillagers.contains(uuid)) {
                        selectedVillagers.remove(uuid);
                        if (uuid.equals(focusedVillager)) focusedVillager = null;
                    } else {
                        selectedVillagers.add(uuid);
                        lastToggledOn = uuid;
                        focusedVillager = uuid;
                    }
                    return true;
                }
            }

            // Template button
            if (inViewport) for (int idx = 0; idx < filteredUuids.size(); idx++) {
                int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
                if (rowY + CELL_H < gridTop) continue;
                if (rowY > gridBottom) break;
                int btnY = rowY + (CELL_H - 14) / 2;
                if (mouseX >= templateBtnLeft && mouseX <= templateBtnLeft + templateBtnW
                        && mouseY >= btnY && mouseY <= btnY + 14) {
                    UUID uuid = filteredUuids.get(idx);
                    focusedVillager = uuid;
                    openTemplateModal(uuid);
                    return true;
                }
            }

            // Legend (paint mode toggle)
            if (mouseY >= legendY - 2 && mouseY <= legendY + 11) {
                for (int i = 0; i < ShiftData.ORDINAL_COLORS.length; i++) {
                    int lx = EDGE + 70 + i * legendStep;
                    if (mouseX >= lx - 2 && mouseX <= lx + 40) {
                        shiftPaintOrdinal = (shiftPaintOrdinal == i) ? -1 : i;
                        return true;
                    }
                }
            }

            // Grid cell paint
            if (inViewport && applyCell(mouseX, mouseY)) {
                for (int idx = 0; idx < filteredUuids.size(); idx++) {
                    int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
                    if (rowY + CELL_H < gridTop) continue;
                    if (rowY > gridBottom) break;
                    if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                        focusedVillager = filteredUuids.get(idx);
                        break;
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean modalMouseClicked(double mouseX, double mouseY, int button) {
        if (confirmDeleteActive) {
            if (confirmDeleteYes != null && confirmDeleteYes.mouseClicked(mouseX, mouseY, button)) return true;
            if (confirmDeleteNo != null && confirmDeleteNo.mouseClicked(mouseX, mouseY, button)) return true;
            return true; // consume; confirmation must be answered first
        }
        if (modalSaveAsActive) {
            if (modalSaveAsConfirmButton != null && modalSaveAsConfirmButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (modalSaveAsCancelButton != null && modalSaveAsCancelButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (modalSaveAsInput != null && modalSaveAsInput.mouseClicked(mouseX, mouseY, button)) return true;
            return true;
        }
        if (modalRenamingTitle && modalRenameInput != null) {
            if (modalRenameInput.mouseClicked(mouseX, mouseY, button)) return true;
            // Click anywhere else commits the rename.
            commitRename();
            // Fall through so the click can also trigger e.g. selecting a different entry
        }
        if (modalCloseButton != null && modalCloseButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalLoadButton != null && modalLoadButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalDuplicateButton != null && modalDuplicateButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalDeleteButton != null && modalDeleteButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalSaveAsButton != null && modalSaveAsButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalAllDaysButton != null && modalAllDaysButton.mouseClicked(mouseX, mouseY, button)) return true;

        // Preview-pane interactions
        ShiftTemplate selected = ShiftTemplateClientStore.find(modalSelectedId);
        if (selected != null) {
            // Click on the title to rename, on user templates
            if (!selected.builtIn() && !modalRenamingTitle
                    && mouseX >= previewTitleX && mouseX <= previewTitleX + previewTitleW
                    && mouseY >= previewTitleY && mouseY <= previewTitleY + previewTitleH) {
                startRename(selected);
                return true;
            }
            // Click on a summary entry toggles paint mode (like the bottom legend)
            if (button == 0 && summaryHitH > 0) {
                for (int i = 0; i < summaryHitX.length; i++) {
                    if (mouseX >= summaryHitX[i] && mouseX <= summaryHitX[i] + summaryHitW[i]
                            && mouseY >= summaryHitY[i] && mouseY <= summaryHitY[i] + summaryHitH) {
                        shiftPaintOrdinal = (shiftPaintOrdinal == i) ? -1 : i;
                        return true;
                    }
                }
            }
            // Click on a preview cell to paint (user templates only)
            if (button == 0 && paintPreviewCell(mouseX, mouseY, selected)) return true;
        }

        // List click
        int mw = Math.min(560, width - 60);
        int mh = Math.min(360, height - 60);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        int listX = mx + 10;
        int listY = my + 30;
        int listW = (mw - 30) / 2;
        int paneH = (my + mh - 10 - 20 - 8) - listY;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + paneH) {
            int idx = entryListIndexAt(listY, modalListScroll, templateRows(), mouseY);
            List<ShiftTemplate> all = ShiftTemplateClientStore.all();
            ShiftTemplate hit = (idx >= 0 && idx < all.size()) ? all.get(idx) : null;
            if (hit != null) {
                ResourceLocation newId = hit.id().equals(modalSelectedId) ? null : hit.id();
                if (!java.util.Objects.equals(newId, modalSelectedId)) {
                    modalSelectedId = newId;
                    shiftPaintOrdinal = -1;
                    modalPreviewScroll = 0; // fresh template, start at the top
                    if (modalRenamingTitle) cancelRename();
                }
                updateModalActionStates();
            }
            return true;
        }
        return true; // consume clicks while modal is active
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modalActive) {
            if (button == 0 && shiftPaintOrdinal >= 0) {
                ShiftTemplate selected = ShiftTemplateClientStore.find(modalSelectedId);
                if (selected != null) paintPreviewCell(mouseX, mouseY, selected);
            }
            return true;
        }
        if (weekPlanModalActive) return true;
        if (viewTab == TAB_DAILY && button == 0 && shiftPaintOrdinal >= 0 && applyCell(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  //? if >=1.21 {
                                  double scrollX, double scrollY) {
                                  //?} else {
                                  /*double scrollY) {
                                  *///?}
        if (modalActive) {
            int mw = Math.min(560, width - 60);
            int mh = Math.min(360, height - 60);
            int mx = (width - mw) / 2;
            int my = (height - mh) / 2;
            int listX = mx + 10;
            int listY = my + 30;
            int listW = (mw - 30) / 2;
            int rightX = listX + listW + 10;
            int rightW = mw - (rightX - mx) - 10;
            int paneH = (my + mh - 10 - 20 - 8) - listY;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + paneH) {
                int listSize = entryListContentHeight(templateRows());
                int visible = paneH - 8;
                int maxScroll = Math.max(0, listSize - visible);
                modalListScroll = (int) Math.max(0, Math.min(maxScroll, modalListScroll - scrollY * 12));
            } else if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= listY && mouseY <= listY + paneH) {
                // Detail pane: scroll the preview (render clamps the range).
                modalPreviewScroll = (int) Math.max(0, modalPreviewScroll - scrollY * 12);
            }
            return true;
        }

        if (weekPlanModalActive) {
            int mw = Math.min(520, width - 60);
            int mh = Math.min(340, height - 60);
            int mx = (width - mw) / 2;
            int my = (height - mh) / 2;
            int listX = mx + 10;
            int listY = my + 30;
            int listW = (mw - 30) / 2;
            int rightX = listX + listW + 10;
            int rightW = mw - (rightX - mx) - 10;
            int paneH = (my + mh - 10 - 20 - 8) - listY;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + paneH) {
                int listSize = entryListContentHeight(weekPlanRows());
                int visible = paneH - 8;
                int maxScroll = Math.max(0, listSize - visible);
                weekPlanListScroll = (int) Math.max(0, Math.min(maxScroll, weekPlanListScroll - scrollY * 12));
            } else if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= listY && mouseY <= listY + paneH) {
                // Detail pane: scroll the day strips (render clamps the range).
                weekPlanPreviewScroll = (int) Math.max(0, weekPlanPreviewScroll - scrollY * 12);
            }
            return true;
        }

        if (mouseY >= gridTop && mouseY <= gridBottom
                && mouseX >= EDGE && mouseX <= width - EDGE) {
            rowScroll = (int) Math.max(0, rowScroll - scrollY * 16);
            clampRowScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY,
                //? if >=1.21 {
                scrollX, scrollY);
                //?} else {
                /*scrollY);
                *///?}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmDeleteActive) {
            if (keyCode == 256) { closeDeleteConfirm(); return true; }
            if (keyCode == 257 || keyCode == 335) {
                Runnable r = confirmDeleteAction;
                closeDeleteConfirm();
                if (r != null) r.run();
                return true;
            }
            return true;
        }
        if (modalActive) {
            if (modalSaveAsActive) {
                if (keyCode == 256) { modalSaveAsActive = false; modalSaveAsInput = null; setFocused(null); return true; }
                if (keyCode == 257 || keyCode == 335) { confirmSaveAs(); return true; }
                if (modalSaveAsInput != null && modalSaveAsInput.keyPressed(keyCode, scanCode, modifiers)) return true;
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (modalRenamingTitle) {
                if (keyCode == 256) { cancelRename(); return true; }
                if (keyCode == 257 || keyCode == 335) { commitRename(); return true; }
                if (modalRenameInput != null && modalRenameInput.keyPressed(keyCode, scanCode, modifiers)) return true;
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == 256) { closeModal(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (weekPlanModalActive) {
            if (weekPlanSaveActive) {
                if (keyCode == 256) { weekPlanSaveActive = false; weekPlanSaveInput = null; setFocused(null); return true; }
                if (keyCode == 257 || keyCode == 335) { confirmWeekPlanSave(); return true; }
                if (weekPlanSaveInput != null && weekPlanSaveInput.keyPressed(keyCode, scanCode, modifiers)) return true;
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (weekPlanRenaming) {
                if (keyCode == 256) { cancelWeekPlanRename(); return true; }
                if (keyCode == 257 || keyCode == 335) { commitWeekPlanRename(); return true; }
                if (weekPlanRenameInput != null && weekPlanRenameInput.keyPressed(keyCode, scanCode, modifiers)) return true;
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == 256) { closeWeekPlanModal(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (modalRenamingTitle && modalRenameInput != null && modalRenameInput.charTyped(chr, modifiers)) return true;
        if (modalSaveAsActive && modalSaveAsInput != null && modalSaveAsInput.charTyped(chr, modifiers)) return true;
        if (weekPlanSaveActive && weekPlanSaveInput != null && weekPlanSaveInput.charTyped(chr, modifiers)) return true;
        if (weekPlanRenaming && weekPlanRenameInput != null && weekPlanRenameInput.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void onClose() {
        if (modalActive) { closeModal(); return; }
        if (weekPlanModalActive) { closeWeekPlanModal(); return; }
        if (this.minecraft != null) this.minecraft.setScreen(returnScreen);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // -------------------------------------------------------- State helpers

    private void toggleSelectAllOnVisible() {
        if (filteredUuids.isEmpty()) return;
        boolean anyUnselected = false;
        for (UUID uuid : filteredUuids) {
            if (!selectedVillagers.contains(uuid)) { anyUnselected = true; break; }
        }
        for (UUID uuid : filteredUuids) {
            if (anyUnselected) {
                if (selectedVillagers.add(uuid)) lastToggledOn = uuid;
            } else {
                selectedVillagers.remove(uuid);
            }
        }
        if (!anyUnselected) {
            if (!selectedVillagers.contains(focusedVillager)) focusedVillager = null;
        } else if (focusedVillager == null) {
            focusedVillager = lastToggledOn;
        }
    }

    private void rangeSelect(UUID anchor, UUID target) {
        int aIdx = filteredUuids.indexOf(anchor);
        int bIdx = filteredUuids.indexOf(target);
        if (aIdx < 0 || bIdx < 0) return;
        int lo = Math.min(aIdx, bIdx);
        int hi = Math.max(aIdx, bIdx);
        for (int i = lo; i <= hi; i++) selectedVillagers.add(filteredUuids.get(i));
        lastToggledOn = target;
        focusedVillager = target;
    }

    private void refreshShiftVillagers() {
        shiftVillagerUuids = new ArrayList<>();
        shiftVillagerNames.clear();
        shiftVillagerChronotypes.clear();
        shiftVillagerTemplateIds.clear();

        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            UUID uuid = resident.villagerUuid();
            shiftVillagerUuids.add(uuid);
            shiftVillagerNames.put(uuid, resident.name());
            shiftVillagerChronotypes.put(uuid, Chronotype.fromName(resident.chronotype()));
            shiftVillagerTemplateIds.put(uuid, resident.templateId());
            ShiftClientStore.set(uuid, resident.shifts());
        }

        shiftVillagerUuids.sort(Comparator.comparing(
                uuid -> shiftVillagerNames.getOrDefault(uuid, uuid.toString())));
        rebuildFilteredList();
    }

    private void rebuildFilteredList() {
        String q = foldForSearch(searchQuery);
        if (q.isEmpty()) {
            filteredUuids = new ArrayList<>(shiftVillagerUuids);
            return;
        }
        List<UUID> out = new ArrayList<>();
        for (UUID uuid : shiftVillagerUuids) {
            String name = shiftVillagerNames.getOrDefault(uuid, "");
            if (foldForSearch(name).contains(q)) out.add(uuid);
        }
        filteredUuids = out;
    }

    /**
     * Lowercase + diacritic-fold so typing "Petris" matches "Pēteris",
     * "Pavels" matches "Pāvels", and so on. Decomposes characters into base
     * letter + combining marks (NFD) and strips the marks.
     */
    private static String foldForSearch(String s) {
        if (s == null || s.isEmpty()) return "";
        String decomposed = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void clampRowScroll() {
        int contentH = filteredUuids.size() * (CELL_H + CELL_GAP);
        int viewport = gridBottom - gridTop;
        int max = Math.max(0, contentH - viewport);
        if (rowScroll < 0) rowScroll = 0;
        if (rowScroll > max) rowScroll = max;
    }

    private void renderRowScrollbar(GuiGraphics g) {
        int contentH = filteredUuids.size() * (CELL_H + CELL_GAP);
        int viewport = gridBottom - gridTop;
        if (contentH <= viewport) return;
        int barX = width - EDGE + 2;
        int barW = 3;
        if (barX + barW > width - 2) { barX = width - 2 - barW; }
        g.fill(barX, gridTop, barX + barW, gridBottom, 0x40FFFFFF);
        int thumbH = Math.max(20, (int) ((long) viewport * viewport / contentH));
        int maxScroll = contentH - viewport;
        int thumbY = gridTop + (int) ((long) (gridBottom - gridTop - thumbH) * rowScroll / Math.max(1, maxScroll));
        g.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0xA0FFFFFF);
    }

    private void pruneStateAgainstResidents() {
        Set<UUID> known = new HashSet<>(shiftVillagerUuids);
        selectedVillagers.retainAll(known);
        if (focusedVillager != null && !known.contains(focusedVillager)) focusedVillager = null;
        if (lastToggledOn != null && !known.contains(lastToggledOn)) lastToggledOn = null;
    }

    private void queryShiftData() {
        if (shiftQueried) return;
        shiftQueried = true;
        //? if neoforge {
        PacketDistributor.sendToServer(new ProfessionQueryPayload());
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new ProfessionQueryPayload());
        *///?}
    }

    private void updateHeaderButtons() {
        if (applyToSelectedButton != null) {
            int n = selectedVillagers.size();
            // Daily-only: this applies a per-day (24h) template. In the weekly
            // tab use Apply Plan instead, so hide it there.
            applyToSelectedButton.visible = n > 0 && viewTab == TAB_DAILY;
            applyToSelectedButton.setMessage(
                    Component.translatable("townstead.shift.apply_to_selected", n));
        }
        // Weekly toolbar enablement. A "target" is any checked villager, or the
        // focused (highlighted) one as a fallback.
        boolean hasTarget = !selectedVillagers.isEmpty() || focusedVillager != null;
        if (weeklyCopyButton != null) weeklyCopyButton.active = focusedVillager != null;
        if (weeklyPasteButton != null) weeklyPasteButton.active = weeklyWeekClipboard != null && hasTarget;
        // Save a plan from the focused villager's week; apply a plan to targets.
        if (weeklySavePlanButton != null) weeklySavePlanButton.active = focusedVillager != null;
        if (weeklyApplyPlanButton != null) weeklyApplyPlanButton.active = hasTarget;
    }

    private Chronotype chronotypeOf(UUID uuid) {
        return shiftVillagerChronotypes.getOrDefault(uuid, Chronotype.STANDARD);
    }

    private String templateLabelFor(UUID uuid) {
        String id = shiftVillagerTemplateIds.get(uuid);
        ShiftTemplate t = ShiftTemplateClientStore.find(id);
        if (t != null) return t.displayName();
        String name = shiftVillagerNames.getOrDefault(uuid, "???");
        return Component.translatable("townstead.shift.template.custom_prefix", name).getString();
    }

    private void resetAllShifts() {
        int[] defaults = ShiftData.getVanillaDefault();
        for (UUID uuid : filteredUuids) {
            shiftEdits.put(uuid, Arrays.copyOf(defaults, defaults.length));
            shiftVillagerTemplateIds.put(uuid, "");
            //? if neoforge {
            PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            *///?}
        }
    }

    /** Set the given hour to {@code ordinal} for every villager in the filtered list. */
    private void fillHourForAll(int hour, int ordinal) {
        if (hour < 0 || hour >= ShiftData.HOURS_PER_DAY) return;
        if (ordinal < 0 || ordinal >= ShiftData.ORDINAL_TO_ACTIVITY.length) return;
        for (UUID uuid : filteredUuids) {
            int[] existing = shiftEdits.containsKey(uuid) ? shiftEdits.get(uuid) : ShiftClientStore.get(uuid);
            if (existing[hour] == ordinal) continue;
            int[] shifts = Arrays.copyOf(existing, existing.length);
            shifts[hour] = ordinal;
            shiftEdits.put(uuid, shifts);
            shiftVillagerTemplateIds.put(uuid, "");
            //? if neoforge {
            PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(shifts, shifts.length)));
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(shifts, shifts.length)));
            *///?}
        }
    }

    private boolean applyCell(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + ShiftData.HOURS_PER_DAY * cellW) return false;
        if (mouseY < gridTop || mouseY > gridBottom) return false;
        int h = (int) ((mouseX - gridLeft) / cellW);
        if (h < 0 || h >= ShiftData.HOURS_PER_DAY) return false;

        for (int idx = 0; idx < filteredUuids.size(); idx++) {
            int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < gridTop) continue;
            if (rowY > gridBottom) break;
            if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                UUID uuid = filteredUuids.get(idx);
                int[] existing = shiftEdits.containsKey(uuid)
                        ? shiftEdits.get(uuid)
                        : ShiftClientStore.get(uuid);
                int[] shifts = Arrays.copyOf(existing, existing.length);

                if (shiftPaintOrdinal >= 0) {
                    if (shifts[h] == shiftPaintOrdinal) return true;
                    shifts[h] = shiftPaintOrdinal;
                } else {
                    shifts[h] = (shifts[h] + 1) % ShiftData.ORDINAL_TO_ACTIVITY.length;
                }

                shiftEdits.put(uuid, shifts);
                // Editing a row drops its template assignment locally; server will confirm
                shiftVillagerTemplateIds.put(uuid, "");
                //? if neoforge {
                PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(shifts, shifts.length)));
                //?} else if forge {
                /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(shifts, shifts.length)));
                *///?}
                return true;
            }
        }
        return false;
    }

    // ============================================================ Weekly view

    private void applyTabVisibility() {
        boolean weekly = viewTab == TAB_WEEKLY;
        if (resetButton != null) resetButton.visible = !weekly;
        if (weeklyCopyButton != null) weeklyCopyButton.visible = weekly;
        if (weeklyPasteButton != null) weeklyPasteButton.visible = weekly;
        if (weeklyApplyPlanButton != null) weeklyApplyPlanButton.visible = weekly;
        if (weeklySavePlanButton != null) weeklySavePlanButton.visible = weekly;
        // Paint mode is daily-only.
        if (weekly) shiftPaintOrdinal = -1;
    }

    private void switchTab(int tab) {
        if (viewTab == tab) return;
        viewTab = tab;
        if (modalActive) closeModal();
        if (weekPlanModalActive) closeWeekPlanModal();
        applyTabVisibility();
    }

    /**
     * Lazily query a villager's weekly state the first time it becomes visible
     * in the weekly tab. The server replies with a {@code ShiftWeekSyncPayload}
     * that populates {@link ShiftClientStore#getWeek}. Gated per-uuid so each
     * villager is queried at most once.
     */
    private void maybeQueryWeek(UUID uuid) {
        if (uuid == null || !weekQueried.add(uuid)) return;
        //? if neoforge {
        PacketDistributor.sendToServer(new ShiftSetPayload(uuid, new int[0]));
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, new int[0]));
        *///?}
    }

    private int currentDaysPerWeek() {
        CalendarClientStore.Snapshot s = CalendarClientStore.get();
        if (s != null && s.daysPerWeek() > 0) return s.daysPerWeek();
        return 7;
    }

    private int todayDow() {
        CalendarClientStore.Snapshot s = CalendarClientStore.get();
        if (s != null && s.daysPerWeek() > 0) return Math.floorMod(s.dayOfWeek(), s.daysPerWeek());
        return -1;
    }

    private String weekdayShort(int dow) {
        CalendarClientStore.Snapshot s = CalendarClientStore.get();
        if (s != null && s.hasWeekdays() && dow >= 0 && dow < s.weekdays().size()) {
            String v = s.weekdays().get(dow).shortComponent().getString();
            if (v != null && !v.isEmpty()) return v;
        }
        return "D" + (dow + 1);
    }

    private String weekdayLong(int dow) {
        CalendarClientStore.Snapshot s = CalendarClientStore.get();
        if (s != null && s.hasWeekdays() && dow >= 0 && dow < s.weekdays().size()) {
            String v = s.weekdays().get(dow).longComponent().getString();
            if (v != null && !v.isEmpty()) return v;
        }
        return Component.translatable("townstead.shift.weekly.fallback").getString() + " " + (dow + 1);
    }

    private void renderWeeklyBody(GuiGraphics g, int mouseX, int mouseY) {
        weekCols = Math.max(1, currentDaysPerWeek());
        weekCellW = Math.max(8, (gridRight - gridLeft) / weekCols);
        if (weeklyFocusedDay >= weekCols) weeklyFocusedDay = 0;

        if (CalendarClientStore.get() == null) {
            g.drawCenteredString(this.font, Component.translatable("townstead.shift.weekly.no_calendar"),
                    width / 2, gridTop + 20, 0xFFA0A0A0);
            return;
        }

        int today = todayDow();

        // Weekday header labels (own band, directly above the grid)
        int labelY = gridTop - this.font.lineHeight - 1;
        for (int d = 0; d < weekCols; d++) {
            int cx = gridLeft + d * weekCellW;
            String label = weekdayShort(d);
            boolean isToday = d == today;
            int color = isToday ? 0xFFFFD040 : 0xFFC8C8C8;
            int tw = this.font.width(label);
            int tx = cx + (weekCellW - tw) / 2;
            g.drawString(this.font, label, tx, labelY, color, false);
        }

        g.enableScissor(EDGE - 2, gridTop, width - EDGE + 2, gridBottom);
        for (int idx = 0; idx < filteredUuids.size(); idx++) {
            int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < gridTop) continue;
            if (rowY > gridBottom) break;
            UUID uuid = filteredUuids.get(idx);
            maybeQueryWeek(uuid);

            if (mouseY >= rowY && mouseY < rowY + CELL_H && mouseY >= gridTop && mouseY < gridBottom) {
                g.fill(EDGE - 2, rowY - 1, width - EDGE + 2, rowY + CELL_H, ROW_HOVER);
            }

            renderCheckbox(g, EDGE, rowY + (CELL_H - CHECKBOX_SIZE) / 2, selectedVillagers.contains(uuid));
            renderName(g, uuid, rowY);
            renderWeekRow(g, uuid, rowY, mouseX, mouseY);
            renderModeToggle(g, uuid, rowY, mouseX, mouseY);
        }
        g.disableScissor();

        // Today column: clean tinted band with side lines, from the weekday
        // label down through the rows (no knob).
        if (today >= 0 && today < weekCols) {
            int tx = gridLeft + today * weekCellW;
            int top = labelY - 1;
            g.fill(tx, top, tx + weekCellW - 1, gridBottom, TODAY_COL_TINT);
            g.fill(tx, top, tx + 1, gridBottom, TODAY_COL_BORDER);
            g.fill(tx + weekCellW - 2, top, tx + weekCellW - 1, gridBottom, TODAY_COL_BORDER);
        }

        // Copy/Paste helper line, centered in the gap between the Copy/Paste
        // cluster and the Week Plan cluster (skipped if there isn't room).
        String help;
        if (weeklyWeekClipboard != null) {
            int n = selectedVillagers.isEmpty() ? (focusedVillager != null ? 1 : 0) : selectedVillagers.size();
            help = Component.translatable("townstead.shift.weekly.clipboard", weeklyClipboardName, n).getString();
        } else if (focusedVillager != null) {
            help = Component.translatable("townstead.shift.weekly.copy_hint",
                    shiftVillagerNames.getOrDefault(focusedVillager, "?")).getString();
        } else {
            help = Component.translatable("townstead.shift.weekly.copy_none").getString();
        }
        int gapStart = EDGE + 2 * weeklyEditW + WEEKLY_TOOL_GAP + 8;        // right of the Paste button
        int gapEnd = width - EDGE - weeklyPlanW * 2 - WEEKLY_TOOL_GAP - 8;  // left of the Save Plan button
        int textW = this.font.width(help);
        if (gapEnd - gapStart >= textW) {
            g.drawString(this.font, help, (gapStart + gapEnd - textW) / 2, legendY + 2, 0xFF9098A2, false);
        }
    }

    private void renderWeekRow(GuiGraphics g, UUID uuid, int rowY, int mouseX, int mouseY) {
        ShiftClientStore.WeekState ws = ShiftClientStore.getWeek(uuid);
        int stripLeft = gridLeft;
        int stripRight = gridLeft + weekCols * weekCellW;
        int ch = CELL_H - 1;

        if (!ws.isWeekly()) {
            // Daily mode: show the villager's actual 24h schedule as one dimmed
            // strip behind a small "Daily" tag, so you still see what they do.
            int tagW = 42;
            int stripX = stripLeft + tagW + 2;
            int stripW = Math.max(1, stripRight - 1 - stripX);
            g.fill(stripLeft, rowY, stripLeft + tagW, rowY + ch, 0xFF2A2F38);
            drawCellBorder(g, stripLeft, rowY, tagW, ch, 0xFF455565);
            g.drawString(this.font, Component.translatable("townstead.shift.weekly.daily_tag"),
                    stripLeft + 5, rowY + (CELL_H - this.font.lineHeight) / 2, 0xFFB6BCC4, false);
            drawMiniStrip(g, stripX, rowY, stripW, ch, ShiftClientStore.get(uuid), false);
            drawCellBorder(g, stripX, rowY, stripW, ch, 0x66000000);
            return;
        }

        for (int d = 0; d < weekCols; d++) {
            int cellX = gridLeft + d * weekCellW;
            int cw = weekCellW - 2;
            String tplId = ws.dayTemplate(d);
            ShiftTemplate t = (tplId == null || tplId.isEmpty()) ? null : ShiftTemplateClientStore.find(tplId);

            if (t != null) {
                drawMiniStrip(g, cellX, rowY, cw, ch, t.copyShifts(), true);
            } else {
                // Unassigned: this day uses the daily fallback. Muted box + dash.
                g.fill(cellX, rowY, cellX + cw, rowY + ch, WEEK_FALLBACK_FILL);
                String dash = "–"; // en dash
                int dw = this.font.width(dash);
                g.drawString(this.font, dash, cellX + (cw - dw) / 2,
                        rowY + (CELL_H - this.font.lineHeight) / 2, 0xFF6A7078, false);
            }
            drawCellBorder(g, cellX, rowY, cw, ch, 0x66000000);

            if (mouseX >= cellX && mouseX < cellX + cw && mouseY >= rowY && mouseY < rowY + ch) {
                g.fill(cellX, rowY, cellX + cw, rowY + ch, 0x30FFFFFF);
                drawCellBorder(g, cellX, rowY, cw, ch, 0xFFFFD040);
            }
        }
    }

    private void drawCellBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Paint a 24-hour activity strip compressed into w pixels. */
    private void drawMiniStrip(GuiGraphics g, int x, int y, int w, int h, int[] shifts, boolean enabled) {
        if (shifts == null || shifts.length != ShiftData.HOURS_PER_DAY || w <= 0) {
            g.fill(x, y, x + w, y + h, 0x30FFFFFF);
            return;
        }
        for (int px = 0; px < w; px++) {
            int hour = (px * ShiftData.HOURS_PER_DAY) / w;
            if (hour < 0) hour = 0;
            if (hour >= ShiftData.HOURS_PER_DAY) hour = ShiftData.HOURS_PER_DAY - 1;
            int ord = shifts[hour];
            if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;
            int color = ShiftData.ORDINAL_COLORS[ord];
            if (!enabled) color = (color & 0x00FFFFFF) | 0x60000000;
            g.fill(x + px, y, x + px + 1, y + h, color);
        }
    }

    /**
     * Like {@link #drawMiniStrip} but draws the 24 hours as discrete cells with a
     * 1px separator between them (the dark background shows through), matching the
     * daily grid's per-hour look. {@code cellW} is a FIXED per-hour width so every
     * cell is identical (no rounding drift); the strip spans {@code cellW * 24}.
     * Falls back to touching cells when there isn't room for separators.
     */
    private void drawMiniStripGridded(GuiGraphics g, int x, int y, int cellW, int h, int[] shifts, boolean enabled) {
        int n = ShiftData.HOURS_PER_DAY;
        if (shifts == null || shifts.length != n || cellW <= 0) {
            g.fill(x, y, x + Math.max(1, cellW) * n, y + h, 0x30FFFFFF);
            return;
        }
        int drawW = cellW > 1 ? cellW - 1 : cellW; // 1px separator when there's room
        for (int hh = 0; hh < n; hh++) {
            int cx0 = x + hh * cellW;
            int ord = shifts[hh];
            if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;
            int color = ShiftData.ORDINAL_COLORS[ord];
            if (!enabled) color = (color & 0x00FFFFFF) | 0x60000000;
            g.fill(cx0, y, cx0 + drawW, y + h, color);
        }
    }

    private static final int SEG_ACTIVE_BG = 0xFF3A6EA5;
    private static final int SEG_INACTIVE_BG = 0xFF24282F;
    private static final int SEG_HOVER_BG = 0xFF323844;

    /** Per-row segmented control: [ Daily | Weekly ], active half highlighted. */
    private void renderModeToggle(GuiGraphics g, UUID uuid, int rowY, int mouseX, int mouseY) {
        boolean weekly = ShiftClientStore.getWeek(uuid).isWeekly();
        int btnX = templateBtnLeft;
        int btnY = rowY + (CELL_H - 14) / 2;
        int btnH = 14;
        int halfW = templateBtnW / 2;
        int midX = btnX + halfW;

        drawSegHalf(g, btnX, btnY, halfW, btnH, Component.translatable("townstead.shift.weekly.mode_daily"),
                !weekly, mouseX, mouseY);
        drawSegHalf(g, midX, btnY, templateBtnW - halfW, btnH, Component.translatable("townstead.shift.weekly.mode_weekly"),
                weekly, mouseX, mouseY);

        // outer border + divider
        int border = TEMPLATE_BTN_BORDER;
        g.fill(btnX, btnY, btnX + templateBtnW, btnY + 1, border);
        g.fill(btnX, btnY + btnH - 1, btnX + templateBtnW, btnY + btnH, border);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, border);
        g.fill(btnX + templateBtnW - 1, btnY, btnX + templateBtnW, btnY + btnH, border);
        g.fill(midX, btnY, midX + 1, btnY + btnH, border);
    }

    private void drawSegHalf(GuiGraphics g, int x, int y, int w, int h, Component label,
                             boolean active, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = active ? SEG_ACTIVE_BG : (hovered ? SEG_HOVER_BG : SEG_INACTIVE_BG);
        g.fill(x, y, x + w, y + h, bg);
        int color = active ? 0xFFFFFFFF : 0xFF9098A2;
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - this.font.lineHeight) / 2 + 1, color);
    }

    private void renderWeekHoverTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (CalendarClientStore.get() == null) return;
        if (mouseY < gridTop || mouseY > gridBottom) return;
        if (mouseX < gridLeft || mouseX >= gridLeft + weekCols * weekCellW) return;
        int d = (mouseX - gridLeft) / weekCellW;
        if (d < 0 || d >= weekCols) return;
        for (int idx = 0; idx < filteredUuids.size(); idx++) {
            int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < gridTop) continue;
            if (rowY > gridBottom) break;
            if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                UUID uuid = filteredUuids.get(idx);
                String villager = shiftVillagerNames.getOrDefault(uuid, "???");
                String dayLabel = weekdayLong(d);
                String tplLabel = effectiveDayLabel(uuid, d);
                g.renderTooltip(this.font,
                        Component.literal(villager + " - " + dayLabel + ": " + tplLabel), mouseX, mouseY);
                return;
            }
        }
    }

    private String effectiveDayLabel(UUID uuid, int day) {
        ShiftClientStore.WeekState ws = ShiftClientStore.getWeek(uuid);
        if (!ws.isWeekly()) return Component.translatable("townstead.shift.weekly.mode_daily").getString();
        String id = ws.dayTemplate(day);
        ShiftTemplate t = (id == null || id.isEmpty()) ? null : ShiftTemplateClientStore.find(id);
        if (t != null) return t.displayName();
        return Component.translatable("townstead.shift.weekly.fallback").getString();
    }

    // -- Weekly input -------------------------------------------------------

    private boolean weeklyMouseClicked(double mouseX, double mouseY, int button) {
        boolean inViewport = mouseY >= gridTop && mouseY <= gridBottom;
        if (!inViewport) return false;

        for (int idx = 0; idx < filteredUuids.size(); idx++) {
            int rowY = gridTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < gridTop) continue;
            if (rowY > gridBottom) break;
            UUID uuid = filteredUuids.get(idx);

            // Checkbox
            int cbY = rowY + (CELL_H - CHECKBOX_SIZE) / 2;
            if (button == 0 && mouseX >= EDGE && mouseX <= EDGE + CHECKBOX_SIZE
                    && mouseY >= cbY && mouseY <= cbY + CHECKBOX_SIZE) {
                if (hasShiftDown() && lastToggledOn != null) {
                    rangeSelect(lastToggledOn, uuid);
                } else if (selectedVillagers.contains(uuid)) {
                    selectedVillagers.remove(uuid);
                    if (uuid.equals(focusedVillager)) focusedVillager = null;
                } else {
                    selectedVillagers.add(uuid);
                    lastToggledOn = uuid;
                    focusedVillager = uuid;
                }
                return true;
            }

            // Mode toggle: left half = Daily, right half = Weekly
            int btnY = rowY + (CELL_H - 14) / 2;
            if (button == 0 && mouseX >= templateBtnLeft && mouseX <= templateBtnLeft + templateBtnW
                    && mouseY >= btnY && mouseY <= btnY + 14) {
                focusedVillager = uuid;
                boolean wantWeekly = mouseX >= templateBtnLeft + templateBtnW / 2;
                setRowMode(uuid, wantWeekly);
                return true;
            }

            // Weekday cell
            if (mouseX >= gridLeft && mouseX < gridLeft + weekCols * weekCellW
                    && mouseY >= rowY && mouseY < rowY + CELL_H) {
                int d = (int) ((mouseX - gridLeft) / weekCellW);
                if (d < 0 || d >= weekCols) return true;
                focusedVillager = uuid;
                weeklyFocusedDay = d;
                if (button == 1) {
                    // right-click clears the day (back to daily fallback); only
                    // meaningful when the villager is already on a weekly plan.
                    if (ShiftClientStore.getWeek(uuid).isWeekly()) assignDayTemplate(uuid, d, "");
                } else if (button == 0) {
                    openDayAssignModal(uuid, d);
                }
                return true;
            }
        }
        return false;
    }

    private void setRowMode(UUID uuid, boolean weekly) {
        ShiftClientStore.WeekState ws = ShiftClientStore.getWeek(uuid);
        sendWeek(uuid, weekly ? ShiftData.MODE_WEEKLY : ShiftData.MODE_DAILY, ws.weekDays());
    }

    private void assignDayTemplate(UUID uuid, int day, String templateId) {
        if (day < 0 || day >= weekCols) return;
        List<String> days = new ArrayList<>(ShiftClientStore.getWeek(uuid).weekDays());
        while (days.size() < weekCols) days.add("");
        days.set(day, templateId == null ? "" : templateId);
        sendWeek(uuid, ShiftData.MODE_WEEKLY, days);
    }

    private void sendWeek(UUID uuid, String mode, List<String> days) {
        List<String> copy = new ArrayList<>(days);
        ShiftClientStore.setWeek(uuid, mode, copy);
        //? if neoforge {
        PacketDistributor.sendToServer(new ShiftWeekSetPayload(uuid, mode, copy));
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new ShiftWeekSetPayload(uuid, mode, copy));
        *///?}
    }

    /** Copy the focused villager's whole week (mode + per-day templates). */
    private void weeklyCopyWeek() {
        if (focusedVillager == null) return;
        weeklyWeekClipboard = ShiftClientStore.getWeek(focusedVillager);
        weeklyClipboardName = shiftVillagerNames.getOrDefault(focusedVillager, "?");
    }

    /** Paste the copied week onto every checked villager (or the focused one). */
    private void weeklyPasteWeek() {
        if (weeklyWeekClipboard == null) return;
        List<UUID> targets = new ArrayList<>(selectedVillagers);
        if (targets.isEmpty() && focusedVillager != null) targets.add(focusedVillager);
        for (UUID uuid : targets) {
            sendWeek(uuid, weeklyWeekClipboard.mode(), weeklyWeekClipboard.weekDays());
        }
    }

    // -- Day-assign modal (reuses the template modal) -----------------------

    private void openDayAssignModal(UUID uuid, int day) {
        modalActive = true;
        modalDayAssign = true;
        modalDayTarget = uuid;
        modalDayIndex = day;
        modalBulkMode = false;
        modalTarget = null;
        modalBulkTargets = List.of();
        modalSaveAsActive = false;
        modalListScroll = 0;
        modalPreviewScroll = 0;
        String cur = ShiftClientStore.getWeek(uuid).dayTemplate(day);
        ShiftTemplate t = (cur == null || cur.isEmpty()) ? null : ShiftTemplateClientStore.find(cur);
        modalSelectedId = t != null ? t.id() : null;
        rebuildModalWidgets();
    }

    private void applyDayTemplateAllDays() {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        if (t == null || modalDayTarget == null) return;
        List<String> days = new ArrayList<>();
        for (int d = 0; d < weekCols; d++) days.add(t.id().toString());
        sendWeek(modalDayTarget, ShiftData.MODE_WEEKLY, days);
        closeModal();
    }

    // ========================================================= Week plan modal

    private void openWeekPlanModalApply() {
        if (!selectedVillagers.isEmpty()) {
            weekPlanBulk = true;
            weekPlanBulkTargets = new ArrayList<>(selectedVillagers);
            weekPlanTarget = null;
        } else if (focusedVillager != null) {
            weekPlanBulk = false;
            weekPlanTarget = focusedVillager;
            weekPlanBulkTargets = List.of();
        } else {
            return;
        }
        weekPlanModalActive = true;
        weekPlanSaveActive = false;
        weekPlanListScroll = 0;
        weekPlanPreviewScroll = 0;
        weekPlanSelectedId = null;
        rebuildWeekPlanWidgets();
    }

    private void openWeekPlanModalSave() {
        if (focusedVillager == null) return;
        weekPlanModalActive = true;
        weekPlanBulk = false;
        weekPlanTarget = focusedVillager;
        weekPlanBulkTargets = List.of();
        weekPlanListScroll = 0;
        weekPlanPreviewScroll = 0;
        weekPlanSelectedId = null;
        weekPlanSaveActive = false;
        rebuildWeekPlanWidgets();
    }

    private void rebuildWeekPlanWidgets() {
        clearWeekPlanWidgets();
        int mw = Math.min(520, width - 60);
        int mh = Math.min(340, height - 60);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        // Single button row across the bottom, full content width: Apply | Save | Delete.
        int btnRowH = 20;
        int btnRowY = my + mh - 10 - btnRowH;
        int barX = mx + 10;
        int barW = mw - 20;
        int gap = 6;
        int bw = (barW - 2 * gap) / 3;

        Component applyLabel = Component.translatable(weekPlanBulk
                ? "townstead.weekplan.apply_btn_bulk" : "townstead.weekplan.apply_btn");
        weekPlanApplyButton = Button.builder(applyLabel,
                b -> applySelectedWeekPlan()).bounds(barX, btnRowY, bw, btnRowH).build();
        weekPlanSaveButton = Button.builder(Component.translatable("townstead.weekplan.save_short"),
                b -> openWeekPlanSaveOverlay()).bounds(barX + bw + gap, btnRowY, bw, btnRowH).build();
        weekPlanDeleteButton = Button.builder(Component.translatable("townstead.shift.template.delete"),
                b -> requestDeleteWeekPlan())
                .bounds(barX + 2 * (bw + gap), btnRowY, barW - 2 * (bw + gap), btnRowH).build();
        weekPlanCloseButton = Button.builder(Component.translatable("townstead.shift.template.close"),
                b -> closeWeekPlanModal()).bounds(mx + mw - 60, my + 6, 50, 18).build();
        updateWeekPlanActionStates();
    }

    private void updateWeekPlanActionStates() {
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        boolean hasTarget = weekPlanBulk ? !weekPlanBulkTargets.isEmpty() : weekPlanTarget != null;
        if (weekPlanApplyButton != null) weekPlanApplyButton.active = p != null && hasTarget;
        if (weekPlanDeleteButton != null) weekPlanDeleteButton.active = p != null && !p.builtIn();
        if (weekPlanSaveButton != null) weekPlanSaveButton.active = weekPlanTarget != null;
    }

    private void clearWeekPlanWidgets() {
        weekPlanApplyButton = null;
        weekPlanDeleteButton = null;
        weekPlanSaveButton = null;
        weekPlanCloseButton = null;
        weekPlanSaveConfirm = null;
        weekPlanSaveCancel = null;
        weekPlanSaveInput = null;
    }

    private void closeWeekPlanModal() {
        weekPlanModalActive = false;
        weekPlanSaveActive = false;
        weekPlanBulk = false;
        weekPlanTarget = null;
        weekPlanBulkTargets = List.of();
        weekPlanSelectedId = null;
        weekPlanRenaming = false;
        weekPlanRenameInput = null;
        closeDeleteConfirm();
        clearWeekPlanWidgets();
        setFocused(null);
    }

    private void renderWeekPlanModal(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        g.fill(0, 0, width, height, OVERLAY_DIM);
        int mw = Math.min(520, width - 60);
        int mh = Math.min(340, height - 60);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        g.fill(mx, my, mx + mw, my + mh, MODAL_BG);
        drawBorder(g, mx, my, mw, mh, MODAL_BORDER);

        String header;
        if (weekPlanBulk) {
            header = Component.translatable("townstead.weekplan.title_bulk", weekPlanBulkTargets.size()).getString();
        } else if (weekPlanTarget != null) {
            header = Component.translatable("townstead.weekplan.title_for",
                    shiftVillagerNames.getOrDefault(weekPlanTarget, "???")).getString();
        } else {
            header = Component.translatable("townstead.weekplan.title").getString();
        }
        g.drawString(this.font, Component.literal(header), mx + 12, my + 10, 0xFFFFFFFF, false);

        int listX = mx + 10;
        int listY = my + 30;
        int listW = (mw - 30) / 2;
        int rightX = listX + listW + 10;
        int rightW = mw - (rightX - mx) - 10;
        // Both panes share one height, ending just above the bottom button row,
        // so the plan list and the detail line up 1:1.
        int btnRowY = my + mh - 10 - 20;
        int paneH = (btnRowY - 8) - listY;
        drawBorder(g, listX, listY, listW, paneH, MODAL_BORDER);
        drawBorder(g, rightX, listY, rightW, paneH, MODAL_BORDER);

        renderWeekPlanList(g, listX, listY, listW, paneH, mouseX, mouseY);
        renderWeekPlanPreview(g, rightX, listY, rightW, paneH, mouseX, mouseY, partialTicks);

        if (weekPlanApplyButton != null) weekPlanApplyButton.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanDeleteButton != null) weekPlanDeleteButton.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanSaveButton != null) weekPlanSaveButton.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanCloseButton != null) weekPlanCloseButton.render(g, mouseX, mouseY, partialTicks);

        if (weekPlanSaveActive) renderWeekPlanSaveOverlay(g, mouseX, mouseY, partialTicks);
        if (confirmDeleteActive) renderDeleteConfirm(g, mouseX, mouseY, partialTicks);
    }

    private void renderWeekPlanList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (WeekPlanClientStore.all().isEmpty()) {
            g.drawString(this.font, Component.translatable("townstead.weekplan.none"),
                    x + 6, y + 8, 0xFF808080, false);
            return;
        }
        renderEntryList(g, x, y, w, h, weekPlanListScroll, weekPlanRows(), false, mouseX, mouseY);
    }

    private void renderWeekPlanPreview(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float pt) {
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        int pad = 8;
        if (p == null) {
            g.drawCenteredString(this.font, Component.translatable("townstead.shift.template.unassigned"),
                    x + w / 2, y + h / 2 - 4, 0xFF808080);
            wpTitleW = 0;
            return;
        }

        int titleX = x + pad;
        int titleY = y + pad;
        if (weekPlanRenaming && !p.builtIn() && weekPlanRenameInput != null) {
            weekPlanRenameInput.setX(titleX);
            weekPlanRenameInput.setY(titleY - 2);
            weekPlanRenameInput.render(g, mouseX, mouseY, pt);
            wpTitleX = titleX; wpTitleY = titleY;
            wpTitleW = weekPlanRenameInput.getWidth();
            wpTitleH = weekPlanRenameInput.getHeight();
        } else {
            g.drawString(this.font, Component.literal(p.displayName()), titleX, titleY, 0xFFFFFFFF, false);
            wpTitleX = titleX; wpTitleY = titleY;
            wpTitleW = this.font.width(p.displayName());
            wpTitleH = this.font.lineHeight;
        }
        // Hide the type tag while the rename box is up (the EditBox overlaps it).
        boolean renamingThis = weekPlanRenaming && !p.builtIn() && weekPlanRenameInput != null;
        if (!renamingThis) {
            String tag = p.builtIn()
                    ? Component.translatable("townstead.weekplan.builtin_tag").getString() : "Custom";
            g.drawString(this.font, tag, titleX, titleY + this.font.lineHeight + 2, 0xFFA0A0A0, false);
        }

        // Mini week preview: one row of day strips, with an hour-label header.
        List<String> days = p.dayTemplates();
        int rows = days.size();
        int sx0 = x + pad + 40;
        // Fixed per-hour cell width so every hour cell is identical (no rounding
        // drift); the strip and its border both span pcw * 24.
        int availW = w - pad * 2 - 70;
        int pcw = Math.max(1, availW / ShiftData.HOURS_PER_DAY);
        int stripW = pcw * ShiftData.HOURS_PER_DAY;
        int labelsY = titleY + this.font.lineHeight * 2 + 8;
        // The modal strip is much narrower than the main weekly grid, so all 24
        // hour ticks would collide (the "9101234" mush). Thin them to a stride
        // that keeps ~9px between labels (half-scale 2-digit width plus a gap),
        // rounded up to a clean divisor of the day so the scale reads evenly.
        int stride = Math.max(1, (int) Math.ceil(9.0 / pcw));
        for (int candidate : new int[] { 1, 2, 3, 4, 6, 8, 12 }) {
            if (candidate >= stride) { stride = candidate; break; }
        }
        for (int hh = 0; hh < ShiftData.HOURS_PER_DAY; hh += stride) {
            int cx = sx0 + hh * pcw + pcw / 2;
            String hl = String.valueOf(ShiftData.toDisplayHour(hh));
            g.pose().pushPose();
            g.pose().translate(cx, labelsY, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(this.font, hl, -this.font.width(hl) / 2, 0, 0xFFA0A0A0, false);
            g.pose().popPose();
        }
        // Day strips live in a scrollable viewport so long weeks (7, 12, ...)
        // are all reachable; the hour header above stays fixed.
        int gridY = labelsY + 8;
        int rowH = 12;
        int rowStep = rowH + 2;
        int viewTop = gridY;
        int viewBottom = y + h - 2;
        int viewH = Math.max(0, viewBottom - viewTop);
        int contentH = rows * rowStep;
        int maxScroll = Math.max(0, contentH - viewH);
        weekPlanPreviewScroll = Math.max(0, Math.min(weekPlanPreviewScroll, maxScroll));

        g.enableScissor(x + 1, viewTop, x + w - 1, viewBottom);
        for (int d = 0; d < rows; d++) {
            int ry = viewTop + d * rowStep - weekPlanPreviewScroll;
            if (ry + rowH < viewTop || ry > viewBottom) continue;
            String label = (d < 64) ? weekdayShort(d) : "D" + (d + 1);
            g.drawString(this.font, label, x + pad, ry + 2, 0xFFC0C0C0, false);
            String id = days.get(d);
            ShiftTemplate t = (id == null || id.isEmpty()) ? null : ShiftTemplateClientStore.find(id);
            if (t != null) {
                drawMiniStripGridded(g, sx0, ry, pcw, rowH, t.copyShifts(), true);
            } else {
                g.fill(sx0, ry, sx0 + stripW, ry + rowH, WEEK_FALLBACK_FILL);
            }
            drawCellBorder(g, sx0, ry, stripW, rowH, 0x66000000);
        }
        g.disableScissor();

        // Scrollbar thumb when the days overflow the viewport.
        if (maxScroll > 0 && viewH > 0) {
            int trackX = x + w - 3;
            int thumbH = Math.max(12, viewH * viewH / contentH);
            int thumbY = viewTop + (viewH - thumbH) * weekPlanPreviewScroll / maxScroll;
            g.fill(trackX, viewTop, trackX + 2, viewBottom, 0x33FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    private void startWeekPlanRename(WeekPlan p) {
        if (p == null || p.builtIn()) return;
        weekPlanRenaming = true;
        int titleW = Math.max(140, wpTitleW + 40);
        weekPlanRenameInput = new EditBox(this.font, wpTitleX, wpTitleY - 2, titleW, 14,
                Component.translatable("townstead.weekplan.save_prompt"));
        weekPlanRenameInput.setMaxLength(64);
        weekPlanRenameInput.setValue(p.displayName());
        //? if >=1.21 {
        weekPlanRenameInput.moveCursorToEnd(false);
        //?} else {
        /*weekPlanRenameInput.moveCursorToEnd();
        *///?}
        weekPlanRenameInput.setHighlightPos(0);
        weekPlanRenameInput.setFocused(true);
        setFocused(weekPlanRenameInput);
    }

    private void commitWeekPlanRename() {
        if (!weekPlanRenaming || weekPlanRenameInput == null) return;
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        if (p == null || p.builtIn()) { cancelWeekPlanRename(); return; }
        String name = weekPlanRenameInput.getValue().trim();
        if (name.isEmpty() || name.equals(p.displayName())) { cancelWeekPlanRename(); return; }
        WeekPlanSavePayload payload = new WeekPlanSavePayload(p.id().toString(), name, p.copyDays());
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        cancelWeekPlanRename();
    }

    private void cancelWeekPlanRename() {
        weekPlanRenaming = false;
        weekPlanRenameInput = null;
        setFocused(null);
    }

    private void renderWeekPlanSaveOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // Lift above the modal content so the opaque panel occludes the
        // (text-batched) preview behind it instead of letting it bleed through.
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        g.fill(0, 0, width, height, OVERLAY_DIM);
        int mw = 280;
        int mh = 88;
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        g.fill(mx, my, mx + mw, my + mh, MODAL_BG);
        drawBorder(g, mx, my, mw, mh, MODAL_BORDER);
        g.drawString(this.font, Component.translatable("townstead.weekplan.save_prompt"),
                mx + 12, my + 10, 0xFFFFFFFF, false);
        if (weekPlanSaveInput != null) weekPlanSaveInput.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanSaveConfirm != null) weekPlanSaveConfirm.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanSaveCancel != null) weekPlanSaveCancel.render(g, mouseX, mouseY, partialTicks);
        g.pose().popPose();
    }

    private void openWeekPlanSaveOverlay() {
        if (weekPlanTarget == null) return;
        weekPlanSaveActive = true;
        int mw = 280;
        int mh = 88;
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        weekPlanSaveInput = new EditBox(this.font, mx + 12, my + 24, mw - 24, 18,
                Component.translatable("townstead.weekplan.save_prompt"));
        weekPlanSaveInput.setMaxLength(64);
        weekPlanSaveInput.setValue("Week: " + shiftVillagerNames.getOrDefault(weekPlanTarget, "Villager"));
        weekPlanSaveInput.setFocused(true);
        setFocused(weekPlanSaveInput);
        weekPlanSaveConfirm = Button.builder(Component.translatable("townstead.shift.template.ok"),
                b -> confirmWeekPlanSave()).bounds(mx + mw - 130, my + mh - 26, 60, 20).build();
        weekPlanSaveCancel = Button.builder(Component.translatable("townstead.shift.template.cancel"),
                b -> { weekPlanSaveActive = false; weekPlanSaveInput = null; setFocused(null); })
                .bounds(mx + mw - 66, my + mh - 26, 60, 20).build();
    }

    private void confirmWeekPlanSave() {
        if (weekPlanSaveInput == null || weekPlanTarget == null) { weekPlanSaveActive = false; return; }
        String name = weekPlanSaveInput.getValue().trim();
        if (name.isEmpty()) name = "Untitled";
        List<String> days = new ArrayList<>(ShiftClientStore.getWeek(weekPlanTarget).weekDays());
        while (days.size() < weekCols) days.add("");
        WeekPlanSavePayload payload = new WeekPlanSavePayload("", name, days);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        weekPlanSaveActive = false;
        weekPlanSaveInput = null;
        setFocused(null);
    }

    private void applySelectedWeekPlan() {
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        if (p == null) return;
        List<UUID> targets;
        if (weekPlanBulk) targets = new ArrayList<>(weekPlanBulkTargets);
        else if (weekPlanTarget != null) targets = new ArrayList<>(List.of(weekPlanTarget));
        else return;
        if (targets.isEmpty()) return;
        // Local optimistic update
        List<String> days = p.copyDays();
        for (UUID uuid : targets) ShiftClientStore.setWeek(uuid, ShiftData.MODE_WEEKLY, days);
        WeekPlanApplyPayload payload = new WeekPlanApplyPayload(p.id(), targets);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        closeWeekPlanModal();
    }

    /** Ask for confirmation before deleting the selected (user) week plan. */
    private void requestDeleteWeekPlan() {
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        if (p == null || p.builtIn()) return;
        openDeleteConfirm(p.displayName(), this::deleteSelectedWeekPlan);
    }

    private void deleteSelectedWeekPlan() {
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        if (p == null || p.builtIn()) return;
        WeekPlanDeletePayload payload = new WeekPlanDeletePayload(p.id());
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
        weekPlanSelectedId = null;
        updateWeekPlanActionStates();
    }

    private boolean weekPlanModalMouseClicked(double mouseX, double mouseY, int button) {
        if (confirmDeleteActive) {
            if (confirmDeleteYes != null && confirmDeleteYes.mouseClicked(mouseX, mouseY, button)) return true;
            if (confirmDeleteNo != null && confirmDeleteNo.mouseClicked(mouseX, mouseY, button)) return true;
            return true; // consume; confirmation must be answered first
        }
        if (weekPlanSaveActive) {
            if (weekPlanSaveConfirm != null && weekPlanSaveConfirm.mouseClicked(mouseX, mouseY, button)) return true;
            if (weekPlanSaveCancel != null && weekPlanSaveCancel.mouseClicked(mouseX, mouseY, button)) return true;
            if (weekPlanSaveInput != null && weekPlanSaveInput.mouseClicked(mouseX, mouseY, button)) return true;
            return true;
        }
        if (weekPlanRenaming && weekPlanRenameInput != null) {
            if (weekPlanRenameInput.mouseClicked(mouseX, mouseY, button)) return true;
            commitWeekPlanRename(); // click elsewhere commits
        }
        // Click the preview title (custom plans) to rename in place.
        WeekPlan sel = WeekPlanClientStore.find(weekPlanSelectedId);
        if (sel != null && !sel.builtIn() && !weekPlanRenaming && wpTitleW > 0
                && mouseX >= wpTitleX && mouseX <= wpTitleX + wpTitleW
                && mouseY >= wpTitleY && mouseY <= wpTitleY + wpTitleH) {
            startWeekPlanRename(sel);
            return true;
        }
        if (weekPlanCloseButton != null && weekPlanCloseButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (weekPlanApplyButton != null && weekPlanApplyButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (weekPlanDeleteButton != null && weekPlanDeleteButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (weekPlanSaveButton != null && weekPlanSaveButton.mouseClicked(mouseX, mouseY, button)) return true;

        int mw = Math.min(520, width - 60);
        int mh = Math.min(340, height - 60);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;
        int listX = mx + 10;
        int listY = my + 30;
        int listW = (mw - 30) / 2;
        int paneH = (my + mh - 10 - 20 - 8) - listY;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + paneH) {
            List<WeekPlan> plans = WeekPlanClientStore.all();
            int idx = entryListIndexAt(listY, weekPlanListScroll, weekPlanRows(), mouseY);
            if (idx >= 0 && idx < plans.size()) {
                ResourceLocation id = plans.get(idx).id();
                weekPlanSelectedId = id.equals(weekPlanSelectedId) ? null : id;
                weekPlanPreviewScroll = 0; // fresh plan, start at the top
                updateWeekPlanActionStates();
            }
            return true;
        }
        return true; // consume clicks while modal active
    }

    private String profDisplayName(String professionId) {
        if ("minecraft:none".equals(professionId)) {
            return Component.translatable("townstead.profession.none").getString();
        }
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.parse(professionId);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(professionId);
        *///?}
        String key = "entity." + id.getNamespace() + ".villager." + id.getPath();
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) return translated;
        String path = id.getPath();
        if (path.isEmpty()) return professionId;
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }
}
