package com.aetherianartificer.townstead.client.gui.shift;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.shift.template.Chronotype;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateApplyPayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateClientStore;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateDeletePayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateSavePayload;
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

    private int nameLeft;
    private int gridLeft;
    private int gridRight;
    private int templateBtnLeft;
    private int cellW;
    private int gridTop;
    private int gridBottom;
    private int rowsPerPage = 1;
    private int legendY;
    private int legendStep;

    private Button prevPageButton;
    private Button nextPageButton;
    private Button selectAllButton;
    private Button applyToSelectedButton;

    private int shiftPage = 0;
    private List<UUID> shiftVillagerUuids = List.of();
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
        gridTop = EDGE + HEADER_H;

        int footerBtnY = height - EDGE - 20;
        legendY = footerBtnY - 18;

        int availableH = legendY - gridTop - 8;
        rowsPerPage = Math.max(1, availableH / (CELL_H + CELL_GAP));
        gridBottom = gridTop + rowsPerPage * (CELL_H + CELL_GAP);

        refreshShiftVillagers();
        pruneStateAgainstResidents();

        // Header
        int headerY = EDGE;
        selectAllButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.select_all"),
                b -> toggleSelectAllOnPage())
                .bounds(EDGE, headerY, 56, 20)
                .build());

        applyToSelectedButton = addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.apply_to_selected", 0),
                b -> openTemplateModalBulk())
                .bounds(EDGE + 60, headerY, 180, 20)
                .build());
        applyToSelectedButton.visible = false;

        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                b -> pageDelta(-1))
                .bounds(gridRight - 44, headerY, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                b -> pageDelta(1))
                .bounds(gridRight - 22, headerY, 20, 20)
                .build());

        // Footer
        addRenderableWidget(Button.builder(
                Component.translatable("townstead.gui.back"),
                b -> onClose())
                .bounds(EDGE, footerBtnY, 60, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("townstead.shift.reset"),
                b -> resetAllShifts())
                .bounds(width - EDGE - 70, footerBtnY, 70, 20)
                .build());

        legendStep = Math.max(56,
                (width - EDGE - 80 - (EDGE + 64)) / Math.max(1, ShiftData.ORDINAL_COLORS.length));

        queryShiftData();
        updatePageButtons();
    }

    // ---------------------------------------------------------------- Render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);

        refreshShiftVillagers();
        pruneStateAgainstResidents();
        updatePageButtons();

        // Title + page indicator
        g.drawCenteredString(this.font, getTitle(),
                width / 2, EDGE + 6, 0xFFFFFFFF);
        int totalPages = totalPages();
        String pageText = String.format("%d/%d", shiftPage + 1, totalPages);
        g.drawString(this.font, Component.literal(pageText),
                gridRight - 44 - this.font.width(pageText) - 6, EDGE + 6, 0xFFA0A0A0, false);

        // Hour labels above the grid
        for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
            int displayHour = ShiftData.toDisplayHour(h);
            String label = String.valueOf(displayHour);
            int lx = gridLeft + h * cellW;
            g.pose().pushPose();
            g.pose().translate(lx + cellW / 2.0f, gridTop - 2, 0);
            g.pose().scale(0.5f, 0.5f, 1.0f);
            g.drawString(this.font, label, -this.font.width(label) / 2, -this.font.lineHeight, 0xFFC0C0C0, false);
            g.pose().popPose();
        }

        int startIdx = shiftPage * rowsPerPage;
        int endIdx = Math.min(startIdx + rowsPerPage, shiftVillagerUuids.size());

        for (int row = 0; row < endIdx - startIdx; row++) {
            UUID uuid = shiftVillagerUuids.get(startIdx + row);
            int rowY = gridTop + row * (CELL_H + CELL_GAP);

            // Row hover highlight
            if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                g.fill(EDGE - 2, rowY - 1, width - EDGE + 2, rowY + CELL_H, ROW_HOVER);
            }

            renderCheckbox(g, EDGE, rowY + (CELL_H - CHECKBOX_SIZE) / 2, selectedVillagers.contains(uuid));
            renderName(g, uuid, rowY);
            renderGridRow(g, uuid, rowY, mouseX, mouseY);
            renderTemplateButton(g, uuid, rowY, mouseX, mouseY);
        }

        // Now-line — drawn last over cells so it's visible
        if (minecraft != null && minecraft.level != null && endIdx > startIdx) {
            long dayTime = minecraft.level.getDayTime() % HOURS_PER_DAY_TICKS;
            int nowX = gridLeft + (int) ((dayTime * (long) cellW) / ShiftData.TICKS_PER_HOUR);
            int lineTop = gridTop - 6;
            int lineBottom = gridTop + rowsPerPage * (CELL_H + CELL_GAP) - CELL_GAP + 2;
            g.fill(nowX, lineTop, nowX + 1, lineBottom, NOW_LINE_COLOR);
            g.fill(nowX - 2, lineTop, nowX + 3, lineTop + 2, NOW_LINE_COLOR);
            g.fill(nowX - 1, lineTop + 2, nowX + 2, lineTop + 3, NOW_LINE_COLOR);
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

        // Tooltips suppressed while modal is open (they otherwise stack on top of it)
        if (!modalActive) renderHoverTooltips(g, mouseX, mouseY, startIdx, endIdx);

        // Modal: translate to z=400 so it sits on top of the rest of the GUI,
        // the same trick vanilla uses for tooltip rendering. Higher z in MC's
        // GUI ortho projection is closer to the camera.
        if (modalActive) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400.0F);
            renderModal(g, mouseX, mouseY, partialTicks);
            g.pose().popPose();
        }
    }

    private void renderName(GuiGraphics g, UUID uuid, int rowY) {
        String name = shiftVillagerNames.getOrDefault(uuid, "???");
        String truncated = name;
        while (this.font.width(truncated) > NAME_W - 2 && truncated.length() > 1) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        if (!truncated.equals(name)) truncated += "..";
        g.drawString(this.font, truncated, nameLeft,
                rowY + (CELL_H - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
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

    private void renderHoverTooltips(GuiGraphics g, int mouseX, int mouseY, int startIdx, int endIdx) {
        // Name tooltip
        for (int row = 0; row < endIdx - startIdx; row++) {
            int rowY = gridTop + row * (CELL_H + CELL_GAP);
            if (mouseX >= nameLeft && mouseX < gridLeft - 4
                    && mouseY >= rowY && mouseY < rowY + CELL_H) {
                UUID uuid = shiftVillagerUuids.get(startIdx + row);
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
                for (int row = 0; row < endIdx - startIdx; row++) {
                    int rowY = gridTop + row * (CELL_H + CELL_GAP);
                    if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                        UUID uuid = shiftVillagerUuids.get(startIdx + row);
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
        if (modalBulkMode) {
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
        renderModalPreview(g, rightX, rightY, rightW, listH - 32);

        // Close button (X)
        if (modalCloseButton != null) modalCloseButton.render(g, mouseX, mouseY, partialTicks);
        if (modalLoadButton != null) modalLoadButton.render(g, mouseX, mouseY, partialTicks);
        if (modalDuplicateButton != null) modalDuplicateButton.render(g, mouseX, mouseY, partialTicks);
        if (modalDeleteButton != null) modalDeleteButton.render(g, mouseX, mouseY, partialTicks);
        if (modalSaveAsButton != null) modalSaveAsButton.render(g, mouseX, mouseY, partialTicks);

        if (modalSaveAsActive) {
            renderSaveAsOverlay(g, mouseX, mouseY, partialTicks);
        }
    }

    private void renderModalList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<ShiftTemplate> templates = ShiftTemplateClientStore.all();
        int entryH = 18;
        int innerX = x + 2;
        int innerY = y + 2;
        int innerR = x + w - 2;
        int innerB = y + h - 2;

        int dy = innerY - modalListScroll;
        String assignedId = modalTarget != null ? shiftVillagerTemplateIds.get(modalTarget) : null;

        for (ShiftTemplate t : templates) {
            if (dy + entryH >= innerY && dy <= innerB) {
                int yT = Math.max(dy, innerY);
                int yB = Math.min(dy + entryH - 1, innerB);
                boolean selected = t.id().equals(modalSelectedId);
                boolean hovered = mouseX >= innerX && mouseX < innerR && mouseY >= yT && mouseY <= yB;
                if (selected) g.fill(innerX, yT, innerR, yB, LIST_SELECTED_BG);
                else if (hovered) g.fill(innerX, yT, innerR, yB, LIST_HOVER_BG);

                // Assigned indicator (•)
                int dotX = innerX + 4;
                int dotY = dy + entryH / 2 - 2;
                if (t.id().toString().equals(assignedId)) {
                    g.fill(dotX, dotY, dotX + 4, dotY + 4, LIST_ASSIGNED_TAG);
                }

                String label = t.displayName();
                int textX = dotX + 8;
                int maxNameW = (innerR - 4) - textX;
                String trunc = label;
                while (this.font.width(trunc) > maxNameW && trunc.length() > 1) {
                    trunc = trunc.substring(0, trunc.length() - 1);
                }
                if (!trunc.equals(label)) trunc += "..";
                int textColor = t.builtIn() ? 0xFFE0E0E0 : 0xFFC9F0FF;
                g.drawString(this.font, trunc, textX, dy + (entryH - this.font.lineHeight) / 2 + 1, textColor, false);
            }
            dy += entryH;
        }

        // Built-in vs user divider
        int builtInCount = 0;
        for (ShiftTemplate t : templates) if (t.builtIn()) builtInCount++;
        if (builtInCount > 0 && builtInCount < templates.size()) {
            int divY = innerY + builtInCount * entryH - modalListScroll;
            if (divY > innerY && divY < innerB) {
                g.fill(innerX + 4, divY, innerR - 4, divY + 1, 0xFF455565);
            }
        }
    }

    private void renderModalPreview(GuiGraphics g, int x, int y, int w, int h) {
        ShiftTemplate t = ShiftTemplateClientStore.find(modalSelectedId);
        int padding = 8;
        if (t == null) {
            String prompt = Component.translatable("townstead.shift.template.unassigned").getString();
            g.drawCenteredString(this.font, Component.literal(prompt), x + w / 2, y + h / 2 - 4, 0xFF808080);
            return;
        }

        // Name
        g.drawString(this.font, Component.literal(t.displayName()), x + padding, y + padding, 0xFFFFFFFF, false);
        String tag = Component.translatable(
                t.builtIn() ? "townstead.shift.template.builtin_tag" : "townstead.shift.template.unassigned"
        ).getString();
        if (!t.builtIn()) tag = "Custom";
        g.drawString(this.font, Component.literal(tag), x + padding,
                y + padding + this.font.lineHeight + 2, 0xFFA0A0A0, false);

        // Preview grid — same cell height as the outside grid
        int gx = x + padding;
        int gy = y + padding + (this.font.lineHeight + 2) * 2 + 4;
        int gw = w - padding * 2;
        int gh = CELL_H;
        int pcell = Math.max(2, gw / ShiftData.HOURS_PER_DAY);
        int[] shifts = t.copyShifts();
        for (int h2 = 0; h2 < ShiftData.HOURS_PER_DAY; h2++) {
            int cx = gx + h2 * pcell;
            int ord = shifts[h2];
            if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;
            g.fill(cx, gy, cx + pcell - 1, gy + gh, ShiftData.ORDINAL_COLORS[ord]);
        }

        // Hour ticks
        for (int h2 = 0; h2 < ShiftData.HOURS_PER_DAY; h2 += 3) {
            int cx = gx + h2 * pcell;
            String label = String.valueOf(ShiftData.toDisplayHour(h2));
            g.pose().pushPose();
            g.pose().translate(cx + pcell / 2.0f, gy + gh + 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(this.font, label, -this.font.width(label) / 2, 0, 0xFFB0B0B0, false);
            g.pose().popPose();
        }
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
        int btnW = (rightW - 16) / 2;

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
        if (!modalBulkMode) {
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
    }

    private boolean hasApplyTarget() {
        return modalBulkMode ? !modalBulkTargets.isEmpty() : modalTarget != null;
    }

    private void clearModalWidgets() {
        modalLoadButton = null;
        modalDuplicateButton = null;
        modalDeleteButton = null;
        modalSaveAsButton = null;
        modalCloseButton = null;
        modalSaveAsInput = null;
        modalSaveAsConfirmButton = null;
        modalSaveAsCancelButton = null;
    }

    private void closeModal() {
        modalActive = false;
        modalBulkMode = false;
        modalTarget = null;
        modalBulkTargets = List.of();
        modalSelectedId = null;
        modalSaveAsActive = false;
        clearModalWidgets();
        setFocused(null);
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
        List<UUID> targets;
        if (modalBulkMode) targets = new ArrayList<>(modalBulkTargets);
        else if (modalTarget != null) targets = new ArrayList<>(List.of(modalTarget));
        else return;
        if (targets.isEmpty()) return;

        int[] shifts = t.copyShifts();
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

        if (button == 0) {
            int startIdx = shiftPage * rowsPerPage;
            int endIdx = Math.min(startIdx + rowsPerPage, shiftVillagerUuids.size());

            // Checkbox
            for (int row = 0; row < endIdx - startIdx; row++) {
                int rowY = gridTop + row * (CELL_H + CELL_GAP);
                int cbY = rowY + (CELL_H - CHECKBOX_SIZE) / 2;
                if (mouseX >= EDGE && mouseX <= EDGE + CHECKBOX_SIZE
                        && mouseY >= cbY && mouseY <= cbY + CHECKBOX_SIZE) {
                    UUID uuid = shiftVillagerUuids.get(startIdx + row);
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
            for (int row = 0; row < endIdx - startIdx; row++) {
                int rowY = gridTop + row * (CELL_H + CELL_GAP);
                int btnY = rowY + (CELL_H - 14) / 2;
                if (mouseX >= templateBtnLeft && mouseX <= templateBtnLeft + TEMPLATE_BTN_W
                        && mouseY >= btnY && mouseY <= btnY + 14) {
                    UUID uuid = shiftVillagerUuids.get(startIdx + row);
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
            if (applyCell(mouseX, mouseY)) {
                for (int row = 0; row < endIdx - startIdx; row++) {
                    int rowY = gridTop + row * (CELL_H + CELL_GAP);
                    if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                        focusedVillager = shiftVillagerUuids.get(startIdx + row);
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
        if (modalCloseButton != null && modalCloseButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalLoadButton != null && modalLoadButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalDuplicateButton != null && modalDuplicateButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalDeleteButton != null && modalDeleteButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (modalSaveAsButton != null && modalSaveAsButton.mouseClicked(mouseX, mouseY, button)) return true;

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
            int entryH = 18;
            int dy = listY + 2 - modalListScroll;
            for (ShiftTemplate t : ShiftTemplateClientStore.all()) {
                if (mouseY >= dy && mouseY < dy + entryH
                        && dy + entryH > listY + 2 && dy < listY + listH - 2) {
                    modalSelectedId = t.id().equals(modalSelectedId) ? null : t.id();
                    updateModalActionStates();
                    return true;
                }
                dy += entryH;
            }
            return true;
        }
        return true; // consume clicks while modal is active
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modalActive) return true;
        if (button == 0 && shiftPaintOrdinal >= 0 && applyCell(mouseX, mouseY)) {
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
                int entryH = 18;
                int listSize = ShiftTemplateClientStore.all().size() * entryH;
                int visible = listH - 4;
                int maxScroll = Math.max(0, listSize - visible);
                modalListScroll = (int) Math.max(0, Math.min(maxScroll, modalListScroll - scrollY * 12));
            }
            return true;
        }

        if (mouseX >= gridLeft && mouseX <= gridLeft + ShiftData.HOURS_PER_DAY * cellW
                && mouseY >= gridTop && mouseY <= gridBottom) {
            if (scrollY < 0) { pageDelta(1); return true; }
            if (scrollY > 0) { pageDelta(-1); return true; }
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
            if (keyCode == 256) { closeModal(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (modalSaveAsActive && modalSaveAsInput != null && modalSaveAsInput.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void onClose() {
        if (modalActive) { closeModal(); return; }
        if (this.minecraft != null) this.minecraft.setScreen(returnScreen);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // -------------------------------------------------------- State helpers

    private void toggleSelectAllOnPage() {
        int startIdx = shiftPage * rowsPerPage;
        int endIdx = Math.min(startIdx + rowsPerPage, shiftVillagerUuids.size());
        boolean anyUnselected = false;
        for (int i = startIdx; i < endIdx; i++) {
            if (!selectedVillagers.contains(shiftVillagerUuids.get(i))) { anyUnselected = true; break; }
        }
        for (int i = startIdx; i < endIdx; i++) {
            UUID uuid = shiftVillagerUuids.get(i);
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
        int aIdx = shiftVillagerUuids.indexOf(anchor);
        int bIdx = shiftVillagerUuids.indexOf(target);
        if (aIdx < 0 || bIdx < 0) return;
        int lo = Math.min(aIdx, bIdx);
        int hi = Math.max(aIdx, bIdx);
        for (int i = lo; i <= hi; i++) selectedVillagers.add(shiftVillagerUuids.get(i));
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
        shiftPage = Math.max(0, Math.min(shiftPage, totalPages() - 1));
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

    private void pageDelta(int delta) {
        int totalPages = totalPages();
        shiftPage = Math.max(0, Math.min(shiftPage + delta, totalPages - 1));
        updatePageButtons();
    }

    private int totalPages() {
        int count = shiftVillagerUuids.size();
        return Math.max(1, (int) Math.ceil(count / (double) rowsPerPage));
    }

    private void updatePageButtons() {
        if (prevPageButton != null) prevPageButton.active = shiftPage > 0;
        if (nextPageButton != null) nextPageButton.active = shiftPage < totalPages() - 1;
        if (applyToSelectedButton != null) {
            int n = selectedVillagers.size();
            applyToSelectedButton.visible = n > 0;
            applyToSelectedButton.setMessage(
                    Component.translatable("townstead.shift.apply_to_selected", n));
        }
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
        for (UUID uuid : shiftVillagerUuids) {
            shiftEdits.put(uuid, Arrays.copyOf(defaults, defaults.length));
            shiftVillagerTemplateIds.put(uuid, "");
            //? if neoforge {
            PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            *///?}
        }
    }

    private boolean applyCell(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + ShiftData.HOURS_PER_DAY * cellW) return false;
        int h = (int) ((mouseX - gridLeft) / cellW);
        if (h < 0 || h >= ShiftData.HOURS_PER_DAY) return false;

        int startIdx = shiftPage * rowsPerPage;
        int endIdx = Math.min(startIdx + rowsPerPage, shiftVillagerUuids.size());

        for (int row = 0; row < endIdx - startIdx; row++) {
            int rowY = gridTop + row * (CELL_H + CELL_GAP);
            if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                UUID uuid = shiftVillagerUuids.get(startIdx + row);
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
