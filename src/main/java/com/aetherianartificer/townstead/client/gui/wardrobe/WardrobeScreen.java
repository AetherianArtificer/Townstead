package com.aetherianartificer.townstead.client.gui.wardrobe;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeAssignPayload;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeClientStore;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeTemplate;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Wardrobe: the Shift Schedule's weekly grid, with outfit templates in the cells. A pinned
 * Village row sits on top; an empty villager cell follows it, and an empty Village cell dresses
 * for the weather. Work hours always wear work clothes, whatever the cell says.
 */
public class WardrobeScreen extends Screen {

    private static final int EDGE = 18;
    private static final int HEADER_H = 28;
    private static final int NAME_W = 96;
    private static final int CELL_H = 18;
    private static final int CELL_GAP = 2;
    private static final int TODAY_COL_TINT = 0x33FFD040;
    private static final int TODAY_COL_BORDER = 0xFFFFD040;
    private static final int FALLBACK_FILL = 0x30FFFFFF;
    private static final int CELL_FILL = 0xFF2A2F38;
    private static final int VILLAGE_ROW_TINT = 0x14FFD040;
    private static final int ROW_HOVER = 0x18FFFFFF;
    private static final int OVERLAY_DIM = 0xC0000000;
    private static final int MODAL_BG = 0xFF1B1F26;
    private static final int MODAL_BORDER = 0xFF455565;
    private static final int LIST_HOVER_BG = 0x40FFFFFF;
    private static final int LIST_SELECTED_BG = 0x60FFCC44;
    private static final int WORK_MARK = 0xFFB6BCC4;

    private final Screen returnScreen;

    private int nameLeft, gridLeft, gridRight, gridTop, gridBottom;
    private int weekCols = 7;
    private int weekCellW;
    private int rowScroll;
    private boolean queried;

    private List<UUID> villagers = new ArrayList<>();

    // The picker: which cell it edits and what it offers.
    private boolean modalActive;
    private @Nullable UUID modalTarget;
    private int modalDay;
    private boolean modalAllDays;
    private int modalScroll;
    private Button modalAllDaysButton, modalCloseButton, modalClearButton;
    private Button backButton;

    public WardrobeScreen(Screen returnScreen) {
        super(Component.translatable("townstead.wardrobe.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();
        nameLeft = EDGE;
        int span = width - 2 * EDGE;
        int nameW = Math.max(60, Math.min(NAME_W, span * 20 / 100));
        gridLeft = nameLeft + nameW + 6;
        gridRight = width - EDGE;
        gridTop = EDGE + HEADER_H + 14;
        int footerBtnY = height - EDGE - 20;
        gridBottom = footerBtnY - 26;
        weekCols = Math.max(1, daysPerWeek());
        weekCellW = Math.max(8, (gridRight - gridLeft) / weekCols);

        backButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.back"),
                b -> onClose()).bounds(EDGE, footerBtnY, 70, 20).build());

        modalAllDaysButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.all_days"),
                b -> modalAllDays = !modalAllDays).bounds(0, 0, 90, 20).build());
        modalClearButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.clear"),
                b -> { send(modalTarget, modalDay(), ""); closeModal(); }).bounds(0, 0, 70, 20).build());
        modalCloseButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.close"),
                b -> closeModal()).bounds(0, 0, 70, 20).build());
        layoutModalButtons();
        applyModalVisibility();

        refreshVillagers();
        if (!queried) {
            queried = true;
            //? if neoforge {
            PacketDistributor.sendToServer(new ProfessionQueryPayload());
            PacketDistributor.sendToServer(WardrobeAssignPayload.query());
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ProfessionQueryPayload());
            TownsteadNetwork.sendToServer(WardrobeAssignPayload.query());
            *///?}
        }
    }

    private void refreshVillagers() {
        List<UUID> out = new ArrayList<>();
        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            out.add(resident.villagerUuid());
        }
        out.sort(Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));
        villagers = out;
    }

    private String nameOf(UUID uuid) {
        VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(uuid);
        return resident == null ? uuid.toString() : resident.name();
    }

    // ---------------------------------------------------------------- Calendar

    private int daysPerWeek() {
        CalendarClientStore.Snapshot s = CalendarClientStore.get();
        return s != null && s.daysPerWeek() > 0 ? s.daysPerWeek() : 7;
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

    // ---------------------------------------------------------------- Render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        //? if forge {
        /*renderBackground(g);
        *///?}
        super.render(g, mouseX, mouseY, partialTicks);
        refreshVillagers();
        weekCols = Math.max(1, daysPerWeek());
        weekCellW = Math.max(8, (gridRight - gridLeft) / weekCols);
        clampScroll();

        g.drawCenteredString(this.font, this.title, width / 2, EDGE + 6, 0xFFFFFFFF);

        int today = todayDow();
        int labelY = gridTop - this.font.lineHeight - 3;
        for (int d = 0; d < weekCols; d++) {
            String label = weekdayShort(d);
            int cx = gridLeft + d * weekCellW;
            g.drawString(this.font, label, cx + (weekCellW - this.font.width(label)) / 2, labelY,
                    d == today ? 0xFFFFD040 : 0xFFC8C8C8, false);
        }

        // Village row, pinned.
        int villageY = gridTop;
        g.fill(EDGE - 2, villageY - 1, width - EDGE + 2, villageY + CELL_H, VILLAGE_ROW_TINT);
        g.drawString(this.font, Component.translatable("townstead.wardrobe.village"), nameLeft,
                villageY + (CELL_H - this.font.lineHeight) / 2, 0xFFFFD040, false);
        renderRow(g, null, villageY, mouseX, mouseY);

        int rowsTop = villageY + CELL_H + CELL_GAP * 2;
        g.enableScissor(EDGE - 2, rowsTop, width - EDGE + 2, gridBottom);
        for (int idx = 0; idx < villagers.size(); idx++) {
            int rowY = rowsTop + idx * (CELL_H + CELL_GAP) - rowScroll;
            if (rowY + CELL_H < rowsTop) continue;
            if (rowY > gridBottom) break;
            UUID uuid = villagers.get(idx);
            if (!modalActive && mouseY >= rowY && mouseY < rowY + CELL_H && mouseY >= rowsTop && mouseY < gridBottom) {
                g.fill(EDGE - 2, rowY - 1, width - EDGE + 2, rowY + CELL_H, ROW_HOVER);
            }
            String name = this.font.plainSubstrByWidth(nameOf(uuid), gridLeft - nameLeft - 8);
            g.drawString(this.font, name, nameLeft, rowY + (CELL_H - this.font.lineHeight) / 2, 0xFFE8E8E8, false);
            renderRow(g, uuid, rowY, mouseX, mouseY);
        }
        g.disableScissor();

        if (today >= 0 && today < weekCols) {
            int tx = gridLeft + today * weekCellW;
            g.fill(tx, labelY - 1, tx + weekCellW - 1, gridBottom, TODAY_COL_TINT);
            g.fill(tx, labelY - 1, tx + 1, gridBottom, TODAY_COL_BORDER);
            g.fill(tx + weekCellW - 2, labelY - 1, tx + weekCellW - 1, gridBottom, TODAY_COL_BORDER);
        }

        String hint = Component.translatable("townstead.wardrobe.hint").getString();
        g.drawString(this.font, hint, EDGE + 76, height - EDGE - 20 + 6, 0xFF9098A2, false);

        if (!modalActive) renderCellTooltip(g, mouseX, mouseY, rowsTop);
        if (modalActive) renderModal(g, mouseX, mouseY);
    }

    private void renderRow(GuiGraphics g, @Nullable UUID uuid, int rowY, int mouseX, int mouseY) {
        int ch = CELL_H - 1;
        for (int d = 0; d < weekCols; d++) {
            int cellX = gridLeft + d * weekCellW;
            int cw = weekCellW - 2;
            String id = uuid == null ? WardrobeClientStore.village(d) : WardrobeClientStore.villager(uuid, d);
            WardrobeTemplate template = WardrobeClientStore.template(id);
            String label;
            int color;
            if (template != null) {
                g.fill(cellX, rowY, cellX + cw, rowY + ch, CELL_FILL);
                label = templateName(template);
                color = 0xFFFFFFFF;
            } else if (uuid == null) {
                g.fill(cellX, rowY, cellX + cw, rowY + ch, CELL_FILL);
                label = Component.translatable("townstead.wardrobe.weather").getString();
                color = 0xFFB6BCC4;
            } else {
                g.fill(cellX, rowY, cellX + cw, rowY + ch, FALLBACK_FILL);
                label = "–";
                color = 0xFF6A7078;
            }
            String shown = this.font.plainSubstrByWidth(label, cw - 6);
            g.drawString(this.font, shown, cellX + (cw - this.font.width(shown)) / 2,
                    rowY + (CELL_H - this.font.lineHeight) / 2, color, false);
            if (uuid != null && worksOn(uuid, d)) {
                // A work day: a small mark, because work hours keep work clothes.
                g.fill(cellX + 1, rowY + 1, cellX + 3, rowY + ch - 1, WORK_MARK);
            }
            drawCellBorder(g, cellX, rowY, cw, ch, 0x66000000);
            if (!modalActive && mouseX >= cellX && mouseX < cellX + cw && mouseY >= rowY && mouseY < rowY + ch
                    && (uuid == null || mouseY >= gridTop + CELL_H + CELL_GAP * 2) && mouseY < gridBottom) {
                g.fill(cellX, rowY, cellX + cw, rowY + ch, 0x30FFFFFF);
                drawCellBorder(g, cellX, rowY, cw, ch, 0xFFFFD040);
            }
        }
    }

    /** Whether the villager has work hours on that weekday, from the shift data already synced. */
    private boolean worksOn(UUID uuid, int day) {
        ShiftClientStore.WeekState week = ShiftClientStore.getWeek(uuid);
        if (week.isWeekly()) {
            String template = week.dayTemplate(day);
            return template != null && !template.isEmpty() && !template.endsWith("day_off");
        }
        for (int hour : ShiftClientStore.get(uuid)) {
            if (hour == ShiftData.ORD_WORK) return true;
        }
        return false;
    }

    private void renderCellTooltip(GuiGraphics g, int mouseX, int mouseY, int rowsTop) {
        Cell cell = cellAt(mouseX, mouseY, rowsTop);
        if (cell == null) return;
        String id = cell.villager == null ? WardrobeClientStore.village(cell.day)
                : WardrobeClientStore.villager(cell.villager, cell.day);
        WardrobeTemplate template = WardrobeClientStore.template(id);
        List<Component> lines = new ArrayList<>();
        if (template != null) {
            lines.add(Component.literal(templateName(template)));
            lines.addAll(describe(template));
        } else if (cell.villager == null) {
            lines.add(Component.translatable("townstead.wardrobe.weather"));
            lines.add(Component.translatable("townstead.wardrobe.weather_desc"));
        } else {
            lines.add(Component.translatable("townstead.wardrobe.inherits"));
            WardrobeTemplate village = WardrobeClientStore.template(WardrobeClientStore.village(cell.day));
            lines.add(village != null ? Component.literal(templateName(village))
                    : Component.translatable("townstead.wardrobe.weather"));
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private List<Component> describe(WardrobeTemplate template) {
        List<Component> out = new ArrayList<>();
        String[] layerKeys = {"base", "outerwear", "accessory"};
        for (int i = 0; i < WardrobeTemplate.LAYERS.length; i++) {
            int requirement = template.requirements()[i];
            if (requirement == WardrobeTemplate.NO_RULE) continue;
            String reqKey = switch (requirement) {
                case WardrobeTemplate.REQUIRED -> "townstead.wardrobe.req.required";
                case WardrobeTemplate.PREFERRED -> "townstead.wardrobe.req.preferred";
                default -> "townstead.wardrobe.req.none";
            };
            String selector = template.selectors()[i];
            String text = Component.translatable("townstead.wardrobe.layer." + layerKeys[i]).getString()
                    + ": " + Component.translatable(reqKey).getString()
                    + (selector == null || selector.isEmpty() ? "" : " · " + selector);
            out.add(Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return out;
    }

    static String templateName(WardrobeTemplate template) {
        if (I18n.exists(template.nameKey())) return I18n.get(template.nameKey());
        return template.fallbackName();
    }

    private void drawCellBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ---------------------------------------------------------------- Picker

    private record Cell(@Nullable UUID villager, int day) {}

    private @Nullable Cell cellAt(double mouseX, double mouseY, int rowsTop) {
        if (mouseX < gridLeft || mouseX >= gridLeft + weekCols * weekCellW) return null;
        int day = (int) ((mouseX - gridLeft) / weekCellW);
        if (day < 0 || day >= weekCols) return null;
        if (mouseY >= gridTop && mouseY < gridTop + CELL_H - 1) return new Cell(null, day);
        if (mouseY < rowsTop || mouseY >= gridBottom) return null;
        int idx = (int) ((mouseY - rowsTop + rowScroll) / (CELL_H + CELL_GAP));
        if (idx < 0 || idx >= villagers.size()) return null;
        int rowY = rowsTop + idx * (CELL_H + CELL_GAP) - rowScroll;
        if (mouseY >= rowY + CELL_H - 1) return null;
        return new Cell(villagers.get(idx), day);
    }

    private int modalDay() {
        return modalAllDays ? WardrobeAssignPayload.ALL_DAYS : modalDay;
    }

    private void openModal(Cell cell) {
        modalActive = true;
        modalTarget = cell.villager;
        modalDay = cell.day;
        modalAllDays = false;
        modalScroll = 0;
        applyModalVisibility();
    }

    private void closeModal() {
        modalActive = false;
        applyModalVisibility();
    }

    private void applyModalVisibility() {
        if (modalAllDaysButton != null) modalAllDaysButton.visible = modalActive;
        if (modalClearButton != null) modalClearButton.visible = modalActive;
        if (modalCloseButton != null) modalCloseButton.visible = modalActive;
        if (backButton != null) backButton.active = !modalActive;
    }

    private int modalW() { return Math.min(420, width - 60); }
    private int modalH() { return Math.min(300, height - 60); }
    private int modalX() { return (width - modalW()) / 2; }
    private int modalY() { return (height - modalH()) / 2; }
    private int listX() { return modalX() + 10; }
    private int listY() { return modalY() + 30; }
    private int listW() { return modalW() - 20; }
    private int listH() { return modalY() + modalH() - 10 - 20 - 8 - listY(); }
    private static final int LIST_ROW_H = 24;

    private void layoutModalButtons() {
        int y = modalY() + modalH() - 10 - 20;
        modalAllDaysButton.setX(modalX() + 10);
        modalAllDaysButton.setY(y);
        modalClearButton.setX(modalX() + 10 + 90 + 6);
        modalClearButton.setY(y);
        modalCloseButton.setX(modalX() + modalW() - 10 - 70);
        modalCloseButton.setY(y);
    }

    private List<WardrobeTemplate> pickerRows() {
        return WardrobeClientStore.templates();
    }

    private void renderModal(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, OVERLAY_DIM);
        int mx = modalX(), my = modalY(), mw = modalW(), mh = modalH();
        g.fill(mx, my, mx + mw, my + mh, MODAL_BG);
        drawCellBorder(g, mx, my, mw, mh, MODAL_BORDER);

        String who = modalTarget == null ? Component.translatable("townstead.wardrobe.village").getString()
                : nameOf(modalTarget);
        String when = modalAllDays ? Component.translatable("townstead.wardrobe.every_day").getString()
                : weekdayLong(modalDay);
        g.drawString(this.font, Component.translatable("townstead.wardrobe.assign_day", who, when),
                mx + 10, my + 10, 0xFFFFFFFF, false);

        int lx = listX(), ly = listY(), lw = listW(), lh = listH();
        drawCellBorder(g, lx, ly, lw, lh, MODAL_BORDER);
        String current = modalTarget == null ? WardrobeClientStore.village(modalDay)
                : WardrobeClientStore.villager(modalTarget, modalDay);
        List<WardrobeTemplate> rows = pickerRows();
        g.enableScissor(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1);
        for (int i = 0; i < rows.size(); i++) {
            WardrobeTemplate template = rows.get(i);
            int ry = ly + 4 + i * LIST_ROW_H - modalScroll;
            if (ry + LIST_ROW_H < ly || ry > ly + lh) continue;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + LIST_ROW_H
                    && mouseY >= ly && mouseY < ly + lh;
            if (template.id().toString().equals(current)) g.fill(lx + 2, ry, lx + lw - 2, ry + LIST_ROW_H, LIST_SELECTED_BG);
            else if (hover) g.fill(lx + 2, ry, lx + lw - 2, ry + LIST_ROW_H, LIST_HOVER_BG);
            g.drawString(this.font, templateName(template), lx + 8, ry + 3, 0xFFFFFFFF, false);
            String summary = summaryLine(template);
            g.drawString(this.font, this.font.plainSubstrByWidth(summary, lw - 16), lx + 8, ry + 13, 0xFF9098A2, false);
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("townstead.wardrobe.no_templates"),
                    lx + lw / 2, ly + lh / 2 - 4, 0xFFA0A0A0);
        }
        modalAllDaysButton.setMessage(Component.translatable(modalAllDays
                ? "townstead.wardrobe.all_days_on" : "townstead.wardrobe.all_days"));
        layoutModalButtons();
    }

    private String summaryLine(WardrobeTemplate template) {
        List<String> parts = new ArrayList<>();
        String[] layerKeys = {"base", "outerwear", "accessory"};
        for (int i = 0; i < WardrobeTemplate.LAYERS.length; i++) {
            int requirement = template.requirements()[i];
            if (requirement == WardrobeTemplate.NO_RULE) continue;
            String selector = template.selectors()[i];
            String word = requirement == WardrobeTemplate.NONE
                    ? Component.translatable("townstead.wardrobe.req.none").getString()
                    : (selector == null || selector.isEmpty() ? "*" : selector);
            parts.add(Component.translatable("townstead.wardrobe.layer." + layerKeys[i]).getString() + " " + word);
        }
        return String.join("  ·  ", parts);
    }

    // ---------------------------------------------------------------- Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modalActive) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            int lx = listX(), ly = listY(), lw = listW(), lh = listH();
            if (button == 0 && mouseX >= lx && mouseX < lx + lw && mouseY >= ly && mouseY < ly + lh) {
                int idx = (int) ((mouseY - ly - 4 + modalScroll) / LIST_ROW_H);
                List<WardrobeTemplate> rows = pickerRows();
                if (idx >= 0 && idx < rows.size()) {
                    send(modalTarget, modalDay(), rows.get(idx).id().toString());
                    closeModal();
                }
                return true;
            }
            int mx = modalX(), my = modalY();
            if (mouseX < mx || mouseX >= mx + modalW() || mouseY < my || mouseY >= my + modalH()) {
                closeModal();
                return true;
            }
            return true;
        }
        int rowsTop = gridTop + CELL_H + CELL_GAP * 2;
        Cell cell = cellAt(mouseX, mouseY, rowsTop);
        if (cell != null) {
            if (button == 1) {
                send(cell.villager, cell.day, "");
            } else if (button == 0) {
                openModal(cell);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 //? if >=1.21 {
                                 double scrollX, double scrollY) {
                                 //?} else {
                                 /*double scrollY) {
                                 *///?}
        if (modalActive) {
            int content = pickerRows().size() * LIST_ROW_H + 8;
            int max = Math.max(0, content - listH());
            modalScroll = Math.max(0, Math.min(max, modalScroll - (int) (scrollY * LIST_ROW_H)));
            return true;
        }
        rowScroll -= (int) (scrollY * (CELL_H + CELL_GAP));
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int rowsTop = gridTop + CELL_H + CELL_GAP * 2;
        int content = villagers.size() * (CELL_H + CELL_GAP);
        int max = Math.max(0, content - (gridBottom - rowsTop));
        rowScroll = Math.max(0, Math.min(max, rowScroll));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modalActive && keyCode == 256) {
            closeModal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(@Nullable UUID villager, int day, String policy) {
        WardrobeAssignPayload payload = new WardrobeAssignPayload(villager, day, policy == null ? "" : policy);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(returnScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
