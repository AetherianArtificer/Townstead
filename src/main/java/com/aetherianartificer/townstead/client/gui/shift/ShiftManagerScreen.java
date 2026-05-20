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
    private boolean weekPlanSaveActive = false;
    private EditBox weekPlanSaveInput;
    private Button weekPlanApplyButton, weekPlanDeleteButton, weekPlanSaveButton, weekPlanCloseButton;
    private Button weekPlanSaveConfirm, weekPlanSaveCancel;

    private int nameLeft;
    private int gridLeft;
    private int gridRight;
    private int templateBtnLeft;
    private int cellW;
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

    public ShiftManagerScreen(Screen returnScreen) {
        super(Component.translatable("townstead.shift.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();

        nameLeft = EDGE + CHECKBOX_SIZE + 6;
        gridLeft = nameLeft + NAME_W + 6;
        templateBtnLeft = width - EDGE - TEMPLATE_BTN_W;
        gridRight = templateBtnLeft - 8;
        cellW = Math.max(8, (gridRight - gridLeft) / ShiftData.HOURS_PER_DAY);
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
        int toolGap = 6;
        int editW = 92;
        weeklyCopyButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.weekly.copy_day"), b -> weeklyCopyWeek())
                .bounds(EDGE, toolY, editW, toolH).build());
        weeklyPasteButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.weekly.paste_day"), b -> weeklyPasteWeek())
                .bounds(EDGE + editW + toolGap, toolY, editW, toolH).build());
        int planW = 130;
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
        super.render(g, mouseX, mouseY, partialTicks);

        refreshShiftVillagers();
        pruneStateAgainstResidents();
        updateHeaderButtons();
        // Grid starts below the tab strip, plus a label band. Weekly needs room
        // for the weekday headers; daily needs a roomier hour row. (The weekly
        // usage hint now lives in the footer next to Back.)
        gridTop = tabsY + TAB_H + (viewTab == TAB_WEEKLY ? 12 : 14);
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
        int hourLabelY = gridTop - 9;
        for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
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
        while (this.font.width(truncated) > NAME_W - 2 && truncated.length() > 1) {
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
        boolean hovered = mouseX >= btnX && mouseX <= btnX + TEMPLATE_BTN_W
                && mouseY >= btnY && mouseY <= btnY + btnH;

        // Frame
        g.fill(btnX, btnY, btnX + TEMPLATE_BTN_W, btnY + btnH, TEMPLATE_BTN_BG);
        int border = hovered ? TEMPLATE_BTN_BORDER_HI : TEMPLATE_BTN_BORDER;
        g.fill(btnX, btnY, btnX + TEMPLATE_BTN_W, btnY + 1, border);
        g.fill(btnX, btnY + btnH - 1, btnX + TEMPLATE_BTN_W, btnY + btnH, border);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, border);
        g.fill(btnX + TEMPLATE_BTN_W - 1, btnY, btnX + TEMPLATE_BTN_W, btnY + btnH, border);

        String label = templateLabelFor(uuid);
        int maxLabelW = TEMPLATE_BTN_W - 16;
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
        int cvX = btnX + TEMPLATE_BTN_W - 8;
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

        // Layout
        int listX = mx + 10;
        int listY = my + 30;
        int listW = (mw - 30) / 2;
        int listH = mh - 60;
        int rightX = listX + listW + 10;
        int rightW = mw - (rightX - mx) - 10;
        int rightY = listY;

        drawBorder(g, listX, listY, listW, listH, MODAL_BORDER);
        drawBorder(g, rightX, rightY, rightW, listH - 32, MODAL_BORDER);

        renderModalList(g, listX, listY, listW, listH, mouseX, mouseY);
        renderModalPreview(g, rightX, rightY, rightW, listH - 32, mouseX, mouseY, partialTicks);

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
    }

    private static final int LIST_ENTRY_H = 18;
    private static final int LIST_DIVIDER_GAP = 10;

    private void renderModalList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<ShiftTemplate> templates = ShiftTemplateClientStore.all();
        int innerX = x + 2;
        int innerY = y + 4;
        int innerR = x + w - 2;
        int innerB = y + h - 2;
        String assignedId = modalTarget != null ? shiftVillagerTemplateIds.get(modalTarget) : null;

        int dy = innerY - modalListScroll;
        boolean sawBuiltIn = false;
        boolean dividerDrawn = false;
        for (ShiftTemplate t : templates) {
            if (sawBuiltIn && !t.builtIn() && !dividerDrawn) {
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
                boolean selected = t.id().equals(modalSelectedId);
                boolean hovered = mouseX >= innerX && mouseX < innerR && mouseY >= yT && mouseY <= yB;
                if (selected) g.fill(innerX, yT, innerR, yB, LIST_SELECTED_BG);
                else if (hovered) g.fill(innerX, yT, innerR, yB, LIST_HOVER_BG);

                int dotX = innerX + 6;
                int dotY = dy + LIST_ENTRY_H / 2 - 2;
                if (t.id().toString().equals(assignedId)) {
                    g.fill(dotX, dotY, dotX + 4, dotY + 4, LIST_ASSIGNED_TAG);
                }

                String label = t.displayName();
                int textX = dotX + 8;
                int maxNameW = (innerR - 6) - textX;
                String trunc = label;
                while (this.font.width(trunc) > maxNameW && trunc.length() > 1) {
                    trunc = trunc.substring(0, trunc.length() - 1);
                }
                if (!trunc.equals(label)) trunc += "..";
                g.drawString(this.font, trunc, textX,
                        dy + (LIST_ENTRY_H - this.font.lineHeight) / 2 + 1,
                        0xFFE0E0E0, false);
            }
            dy += LIST_ENTRY_H;
            if (t.builtIn()) sawBuiltIn = true;
        }
    }

    /** Total height the list's content occupies, including the built-in/user divider gap. */
    private int modalListContentHeight() {
        List<ShiftTemplate> templates = ShiftTemplateClientStore.all();
        int h = 0;
        boolean sawBuiltIn = false;
        boolean dividerAdded = false;
        for (ShiftTemplate t : templates) {
            if (sawBuiltIn && !t.builtIn() && !dividerAdded) {
                h += LIST_DIVIDER_GAP;
                dividerAdded = true;
            }
            h += LIST_ENTRY_H;
            if (t.builtIn()) sawBuiltIn = true;
        }
        return h;
    }

    /** Convert a click y inside the list area to a template, accounting for the divider gap. */
    private ShiftTemplate hitTestListEntry(double mouseY, int innerY) {
        List<ShiftTemplate> templates = ShiftTemplateClientStore.all();
        int dy = innerY - modalListScroll;
        boolean sawBuiltIn = false;
        boolean dividerAdded = false;
        for (ShiftTemplate t : templates) {
            if (sawBuiltIn && !t.builtIn() && !dividerAdded) {
                dy += LIST_DIVIDER_GAP;
                dividerAdded = true;
            }
            if (mouseY >= dy && mouseY < dy + LIST_ENTRY_H) return t;
            dy += LIST_ENTRY_H;
            if (t.builtIn()) sawBuiltIn = true;
        }
        return null;
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
        int padding = 10;

        // Title (clickable on user templates for inline rename) / EditBox when renaming
        int titleX = x + padding;
        int titleY = y + padding;
        if (modalRenamingTitle && !t.builtIn() && modalRenameInput != null) {
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

        String tag = t.builtIn() ? "Built-in" : "Custom";
        int tagY = titleY + this.font.lineHeight + 3;
        g.drawString(this.font, Component.literal(tag), titleX, tagY, 0xFFA0A0A0, false);

        // Preview grid — matches the outside grid (CELL_H, same cell colors, same cellW math)
        int gridY = tagY + this.font.lineHeight + 8;
        int gridX = titleX;
        int gridW = w - padding * 2;
        int pcell = Math.max(4, gridW / ShiftData.HOURS_PER_DAY);
        int gridActualW = pcell * ShiftData.HOURS_PER_DAY;

        previewGridX = gridX;
        previewGridY = gridY;
        previewGridCellW = pcell;
        previewGridH = CELL_H;

        // Hour labels above
        for (int h2 = 0; h2 < ShiftData.HOURS_PER_DAY; h2++) {
            int displayHour = ShiftData.toDisplayHour(h2);
            String label = String.valueOf(displayHour);
            int lx = gridX + h2 * pcell;
            g.pose().pushPose();
            g.pose().translate(lx + pcell / 2.0f, gridY - 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(this.font, label, -this.font.width(label) / 2, -this.font.lineHeight, 0xFFC0C0C0, false);
            g.pose().popPose();
        }

        int[] shifts = effectiveTemplateShifts(t);
        boolean editable = !t.builtIn();
        for (int h2 = 0; h2 < ShiftData.HOURS_PER_DAY; h2++) {
            int cx = gridX + h2 * pcell;
            int ord = shifts[h2];
            if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;
            g.fill(cx, gridY, cx + pcell - 1, gridY + CELL_H - 1, ShiftData.ORDINAL_COLORS[ord]);
            // Hover affordance on user templates
            if (editable && mouseX >= cx && mouseX < cx + pcell - 1
                    && mouseY >= gridY && mouseY < gridY + CELL_H - 1) {
                g.fill(cx, gridY, cx + pcell - 1, gridY + CELL_H - 1, 0x40FFFFFF);
            }
        }

        // Activity summary — clickable like the bottom legend. Wraps to a new
        // line when it would overflow the preview pane (small screens).
        int swatchSize = 9;
        int lineH = swatchSize + 6;
        int textOffsetY = swatchSize - this.font.lineHeight + 1; // align text bottom to swatch bottom
        int[] counts = new int[ShiftData.ORDINAL_COLORS.length];
        for (int v : shifts) {
            if (v >= 0 && v < counts.length) counts[v]++;
        }
        summaryHitH = swatchSize + 4;
        int rightBound = gridX + gridActualW;
        int sx = gridX;
        int sy = gridY + CELL_H + 10;
        for (int i = 0; i < counts.length; i++) {
            String label = Component.translatable(ShiftData.ORDINAL_TO_KEY[i]).getString() + " " + counts[i] + "h";
            int entryW = swatchSize + 3 + this.font.width(label);
            if (sx != gridX && sx + entryW > rightBound) { // wrap
                sx = gridX;
                sy += lineH;
            }
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

        // Assigned-to count (below the last summary row)
        int used = countAssignedTo(t);
        int usedY = sy + this.font.lineHeight + 6;
        String usedText = used == 0
                ? Component.translatable("townstead.shift.template.used_none").getString()
                : Component.translatable("townstead.shift.template.used_by", used).getString();
        g.drawString(this.font, usedText, gridX, usedY, 0xFFA0A0A0, false);

        // Editable hint
        if (editable) {
            String hint = Component.translatable("townstead.shift.template.edit_hint").getString();
            int hintW = this.font.width(hint);
            g.drawString(this.font, hint, gridX + gridActualW - hintW, usedY, 0xFF707070, false);
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
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // --------------------------------------------------------- Modal control

    private void openTemplateModal(UUID target) {
        modalActive = true;
        modalBulkMode = false;
        modalTarget = target;
        modalBulkTargets = List.of();
        modalSaveAsActive = false;
        modalListScroll = 0;
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

        int listW = (mw - 30) / 2;
        int rightX = mx + 10 + listW + 10;
        int rightW = mw - (rightX - mx) - 10;
        int btnRowY = my + mh - 30;
        // Two-column rows span exactly the same left/right edges as the
        // full-width button above them (gap of 4 between the two columns).
        int btnW = (rightW - 12) / 2;

        modalLoadButton = Button.builder(
                Component.translatable("townstead.shift.template.load"),
                b -> applySelectedTemplate())
                .bounds(rightX + 4, btnRowY, btnW, 20)
                .build();
        modalDeleteButton = Button.builder(
                Component.translatable("townstead.shift.template.delete"),
                b -> deleteSelectedTemplate())
                .bounds(rightX + 8 + btnW, btnRowY, btnW, 20)
                .build();
        if (modalDayAssign) {
            modalDuplicateButton = null;
            modalSaveAsButton = null;
            modalAllDaysButton = Button.builder(
                    Component.translatable("townstead.shift.weekly.fill_all"),
                    b -> applyDayTemplateAllDays())
                    .bounds(rightX + 4, btnRowY - 22, rightW - 8, 20)
                    .build();
        } else if (!modalBulkMode) {
            modalAllDaysButton = null;
            modalDuplicateButton = Button.builder(
                    Component.translatable("townstead.shift.template.duplicate"),
                    b -> duplicateSelectedTemplate())
                    .bounds(rightX + 4, btnRowY - 22, btnW, 20)
                    .build();
            modalSaveAsButton = Button.builder(
                    Component.translatable("townstead.shift.template.save"),
                    b -> openSaveAsOverlay())
                    .bounds(rightX + 8 + btnW, btnRowY - 22, btnW, 20)
                    .build();
        } else {
            modalDuplicateButton = null;
            modalSaveAsButton = null;
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
                if (mouseX >= templateBtnLeft && mouseX <= templateBtnLeft + TEMPLATE_BTN_W
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
        int listH = mh - 60;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int innerY = listY + 4;
            ShiftTemplate hit = hitTestListEntry(mouseY, innerY);
            if (hit != null) {
                ResourceLocation newId = hit.id().equals(modalSelectedId) ? null : hit.id();
                if (!java.util.Objects.equals(newId, modalSelectedId)) {
                    modalSelectedId = newId;
                    shiftPaintOrdinal = -1;
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
            int listH = mh - 60;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                int listSize = modalListContentHeight();
                int visible = listH - 8;
                int maxScroll = Math.max(0, listSize - visible);
                modalListScroll = (int) Math.max(0, Math.min(maxScroll, modalListScroll - scrollY * 12));
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
            int listH = mh - 60;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                int listSize = WeekPlanClientStore.all().size() * LIST_ENTRY_H;
                int visible = listH - 8;
                int maxScroll = Math.max(0, listSize - visible);
                weekPlanListScroll = (int) Math.max(0, Math.min(maxScroll, weekPlanListScroll - scrollY * 12));
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
        int gapStart = EDGE + 2 * 92 + 6 + 8;        // right of the Paste button
        int gapEnd = width - EDGE - 130 * 2 - 6 - 8; // left of the Save Plan button
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

    private static final int SEG_ACTIVE_BG = 0xFF3A6EA5;
    private static final int SEG_INACTIVE_BG = 0xFF24282F;
    private static final int SEG_HOVER_BG = 0xFF323844;

    /** Per-row segmented control: [ Daily | Weekly ], active half highlighted. */
    private void renderModeToggle(GuiGraphics g, UUID uuid, int rowY, int mouseX, int mouseY) {
        boolean weekly = ShiftClientStore.getWeek(uuid).isWeekly();
        int btnX = templateBtnLeft;
        int btnY = rowY + (CELL_H - 14) / 2;
        int btnH = 14;
        int halfW = TEMPLATE_BTN_W / 2;
        int midX = btnX + halfW;

        drawSegHalf(g, btnX, btnY, halfW, btnH, Component.translatable("townstead.shift.weekly.mode_daily"),
                !weekly, mouseX, mouseY);
        drawSegHalf(g, midX, btnY, TEMPLATE_BTN_W - halfW, btnH, Component.translatable("townstead.shift.weekly.mode_weekly"),
                weekly, mouseX, mouseY);

        // outer border + divider
        int border = TEMPLATE_BTN_BORDER;
        g.fill(btnX, btnY, btnX + TEMPLATE_BTN_W, btnY + 1, border);
        g.fill(btnX, btnY + btnH - 1, btnX + TEMPLATE_BTN_W, btnY + btnH, border);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, border);
        g.fill(btnX + TEMPLATE_BTN_W - 1, btnY, btnX + TEMPLATE_BTN_W, btnY + btnH, border);
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
            if (button == 0 && mouseX >= templateBtnLeft && mouseX <= templateBtnLeft + TEMPLATE_BTN_W
                    && mouseY >= btnY && mouseY <= btnY + 14) {
                focusedVillager = uuid;
                boolean wantWeekly = mouseX >= templateBtnLeft + TEMPLATE_BTN_W / 2;
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
        int btnRowY = my + mh - 30;
        int rightX = mx + 10 + (mw - 30) / 2 + 10;
        int rightW = mw - (rightX - mx) - 10;
        int btnW = (rightW - 8) / 2;

        // Apply is the primary action (full width, on top); Save / Delete below.
        Component applyLabel = Component.translatable(weekPlanBulk
                ? "townstead.weekplan.apply_btn_bulk" : "townstead.weekplan.apply_btn");
        weekPlanApplyButton = Button.builder(applyLabel,
                b -> applySelectedWeekPlan()).bounds(rightX, btnRowY - 22, rightW, 20).build();
        weekPlanSaveButton = Button.builder(Component.translatable("townstead.weekplan.save_short"),
                b -> openWeekPlanSaveOverlay()).bounds(rightX, btnRowY, btnW, 20).build();
        weekPlanDeleteButton = Button.builder(Component.translatable("townstead.shift.template.delete"),
                b -> deleteSelectedWeekPlan()).bounds(rightX + btnW + 8, btnRowY, btnW, 20).build();
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
        int listH = mh - 60;
        int rightX = listX + listW + 10;
        int rightW = mw - (rightX - mx) - 10;
        drawBorder(g, listX, listY, listW, listH, MODAL_BORDER);
        drawBorder(g, rightX, listY, rightW, listH - 32, MODAL_BORDER);

        renderWeekPlanList(g, listX, listY, listW, listH, mouseX, mouseY);
        renderWeekPlanPreview(g, rightX, listY, rightW, listH - 32);

        if (weekPlanApplyButton != null) weekPlanApplyButton.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanDeleteButton != null) weekPlanDeleteButton.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanSaveButton != null) weekPlanSaveButton.render(g, mouseX, mouseY, partialTicks);
        if (weekPlanCloseButton != null) weekPlanCloseButton.render(g, mouseX, mouseY, partialTicks);

        if (weekPlanSaveActive) renderWeekPlanSaveOverlay(g, mouseX, mouseY, partialTicks);
    }

    private void renderWeekPlanList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<WeekPlan> plans = WeekPlanClientStore.all();
        int innerX = x + 2;
        int innerY = y + 4;
        int innerR = x + w - 2;
        int innerB = y + h - 2;
        if (plans.isEmpty()) {
            g.drawString(this.font, Component.translatable("townstead.weekplan.none"),
                    innerX + 4, innerY + 4, 0xFF808080, false);
            return;
        }
        int dy = innerY - weekPlanListScroll;
        boolean sawBuiltIn = false;
        for (WeekPlan p : plans) {
            // Divider line at the built-in -> custom boundary (no vertical gap,
            // so click hit-testing stays a simple fixed-height scan).
            if (sawBuiltIn && !p.builtIn() && dy > innerY && dy < innerB) {
                g.fill(innerX + 6, dy, innerR - 6, dy + 1, 0xFF455565);
            }
            if (dy + LIST_ENTRY_H >= innerY && dy <= innerB) {
                int yT = Math.max(dy, innerY);
                int yB = Math.min(dy + LIST_ENTRY_H - 1, innerB);
                boolean selected = p.id().equals(weekPlanSelectedId);
                boolean hovered = mouseX >= innerX && mouseX < innerR && mouseY >= yT && mouseY <= yB;
                if (selected) g.fill(innerX, yT, innerR, yB, LIST_SELECTED_BG);
                else if (hovered) g.fill(innerX, yT, innerR, yB, LIST_HOVER_BG);
                int color = p.builtIn() ? 0xFFE0E0E0 : 0xFFC9F0FF;
                String label = p.displayName();
                int maxW = (innerR - 6) - (innerX + 6);
                String trunc = label;
                while (this.font.width(trunc) > maxW && trunc.length() > 1) trunc = trunc.substring(0, trunc.length() - 1);
                if (!trunc.equals(label)) trunc += "..";
                g.drawString(this.font, trunc, innerX + 6,
                        dy + (LIST_ENTRY_H - this.font.lineHeight) / 2 + 1, color, false);
            }
            dy += LIST_ENTRY_H;
            if (p.builtIn()) sawBuiltIn = true;
        }
    }

    private void renderWeekPlanPreview(GuiGraphics g, int x, int y, int w, int h) {
        WeekPlan p = WeekPlanClientStore.find(weekPlanSelectedId);
        int pad = 8;
        if (p == null) {
            g.drawCenteredString(this.font, Component.translatable("townstead.shift.template.unassigned"),
                    x + w / 2, y + h / 2 - 4, 0xFF808080);
            return;
        }
        g.drawString(this.font, Component.literal(p.displayName()), x + pad, y + pad, 0xFFFFFFFF, false);
        String tag = p.builtIn()
                ? Component.translatable("townstead.weekplan.builtin_tag").getString() : "Custom";
        g.drawString(this.font, tag, x + pad, y + pad + this.font.lineHeight + 2, 0xFFA0A0A0, false);

        // Mini week preview: one row of day strips
        List<String> days = p.dayTemplates();
        int rows = days.size();
        int gridY = y + pad + this.font.lineHeight * 2 + 8;
        int rowH = 12;
        int stripW = w - pad * 2 - 70;
        for (int d = 0; d < rows; d++) {
            int ry = gridY + d * (rowH + 2);
            if (ry + rowH > y + h - 2) break;
            String label = (d < 64) ? weekdayShort(d) : "D" + (d + 1);
            g.drawString(this.font, label, x + pad, ry + 2, 0xFFC0C0C0, false);
            String id = days.get(d);
            ShiftTemplate t = (id == null || id.isEmpty()) ? null : ShiftTemplateClientStore.find(id);
            int sx = x + pad + 40;
            if (t != null) {
                drawMiniStrip(g, sx, ry, stripW, rowH, t.copyShifts(), true);
            } else {
                g.fill(sx, ry, sx + stripW, ry + rowH, WEEK_FALLBACK_FILL);
            }
        }
    }

    private void renderWeekPlanSaveOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
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
        if (weekPlanSaveActive) {
            if (weekPlanSaveConfirm != null && weekPlanSaveConfirm.mouseClicked(mouseX, mouseY, button)) return true;
            if (weekPlanSaveCancel != null && weekPlanSaveCancel.mouseClicked(mouseX, mouseY, button)) return true;
            if (weekPlanSaveInput != null && weekPlanSaveInput.mouseClicked(mouseX, mouseY, button)) return true;
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
        int listH = mh - 60;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            List<WeekPlan> plans = WeekPlanClientStore.all();
            int dy = listY + 4 - weekPlanListScroll;
            for (WeekPlan p : plans) {
                if (mouseY >= dy && mouseY < dy + LIST_ENTRY_H) {
                    weekPlanSelectedId = p.id().equals(weekPlanSelectedId) ? null : p.id();
                    updateWeekPlanActionStates();
                    break;
                }
                dy += LIST_ENTRY_H;
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
