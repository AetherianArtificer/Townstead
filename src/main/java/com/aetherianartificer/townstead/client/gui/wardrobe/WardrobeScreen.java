package com.aetherianartificer.townstead.client.gui.wardrobe;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeAssignPayload;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeAssignments;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeClientStore;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeSyncPayload;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeTemplate;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Wardrobe: per villager, the outfit they wear at work and on each weekday off shift, plus
 * whether they add warm layers in the cold and light layers in the heat. Cells hold MCA skins
 * picked in MCA's own clothing selector ({@link WardrobeClothingScreen}); an empty day is the villager's own choice. The pinned
 * Village row holds the weather layers for everyone.
 */
public class WardrobeScreen extends Screen {

    private static final int EDGE = 18;
    private static final int HEADER_H = 28;
    private static final int NAME_W = 110;
    private static final int ROW_H = 26;
    private static final int CELL_GAP = 2;
    private static final int WEATHER_W = 40;
    private static final int CHECK_W = 12;
    private static final int MENU_W = 12;
    private static final int TODAY_COL_TINT = 0x33FFD040;
    private static final int TODAY_COL_BORDER = 0xFFFFD040;
    private static final int EMPTY_FILL = 0x30FFFFFF;
    private static final int CELL_FILL = 0xFF2A2F38;
    private static final int WORK_FILL = 0xFF30353F;
    private static final int VILLAGE_ROW_TINT = 0x14FFD040;
    private static final int ROW_HOVER = 0x18FFFFFF;
    private static final int ROW_SELECTED = 0x30FFD040;
    private static final int LOCKED_DIM = 0x90101418;
    private static final int MENU_BG = 0xFF1B1F26;
    private static final int MENU_BORDER = 0xFF455565;
    private static final int MENU_ROW_H = 14;
    private static final int WORK_MARK = 0xFFB6BCC4;
    private static final int ACCENT = 0xFFFFD040;

    /** The row last copied, kept while the game runs. */
    private static @Nullable List<String> clipboard;

    private final Screen returnScreen;

    private int nameLeft, gridLeft, gridRight, weatherLeft, gridTop, gridBottom, rowsTop;
    private int dayCols = 7;
    private int cellW;
    private int rowScroll;

    private List<UUID> villagers = new ArrayList<>();
    private List<UUID> queried = null;

    private @Nullable UUID menuRow;
    private int menuX, menuY;

    private boolean pasteMode;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private Button backButton, householdButton, worksiteButton, pasteButton, cancelButton;

    private enum MenuItem { FILL, COPY, PASTE, PASTE_MANY, CLEAR }

    public WardrobeScreen(Screen returnScreen) {
        super(Component.translatable("townstead.wardrobe.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();
        nameLeft = EDGE;
        int span = width - 2 * EDGE;
        int nameW = Math.max(70, Math.min(NAME_W, span * 20 / 100));
        gridLeft = nameLeft + nameW + 6;
        weatherLeft = width - EDGE - 2 * WEATHER_W;
        gridRight = weatherLeft - 6;
        gridTop = EDGE + HEADER_H + 14;
        rowsTop = gridTop + ROW_H + CELL_GAP * 2;
        int footerY = height - EDGE - 20;
        gridBottom = footerY - 8;
        layoutColumns();

        backButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.back"),
                b -> onClose()).bounds(EDGE, footerY, 70, 20).build());
        int fx = EDGE + 76;
        householdButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.select_household"),
                b -> selectShared(true)).bounds(fx, footerY, 110, 20).build());
        worksiteButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.select_worksite"),
                b -> selectShared(false)).bounds(fx + 114, footerY, 110, 20).build());
        pasteButton = addRenderableWidget(Button.builder(Component.empty(), b -> pasteSelected())
                .bounds(fx + 228, footerY, 90, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("townstead.wardrobe.cancel"),
                b -> setPasteMode(false)).bounds(fx + 322, footerY, 70, 20).build());
        applyPasteMode();

        refreshVillagers();
        if (queried == null) {
            //? if neoforge {
            PacketDistributor.sendToServer(new ProfessionQueryPayload());
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ProfessionQueryPayload());
            *///?}
        }
        queryIfChanged();
    }

    private void layoutColumns() {
        dayCols = Math.max(1, daysPerWeek());
        cellW = Math.max(10, (gridRight - gridLeft) / (dayCols + 1));
    }

    private void refreshVillagers() {
        List<UUID> out = new ArrayList<>();
        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            out.add(resident.villagerUuid());
        }
        out.sort(Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));
        villagers = out;
    }

    private void queryIfChanged() {
        if (queried != null && queried.equals(villagers)) return;
        queried = List.copyOf(villagers);
        send(WardrobeAssignPayload.query(queried));
    }

    private String nameOf(UUID uuid) {
        VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(uuid);
        return resident == null ? uuid.toString() : resident.name();
    }

    private boolean locked(UUID uuid) {
        WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(uuid);
        return resident != null && resident.locked();
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

    // ---------------------------------------------------------------- Geometry

    /** Column 0 is Work, 1..dayCols are weekdays. */
    private int colX(int col) {
        return gridLeft + col * cellW;
    }

    private int weatherX(boolean warm) {
        return warm ? weatherLeft : weatherLeft + WEATHER_W;
    }

    private int rowY(int idx) {
        return rowsTop + idx * (ROW_H + CELL_GAP) - rowScroll;
    }

    private int rowAt(double mouseY) {
        if (mouseY < rowsTop || mouseY >= gridBottom) return -1;
        int idx = (int) ((mouseY - rowsTop + rowScroll) / (ROW_H + CELL_GAP));
        if (idx < 0 || idx >= villagers.size()) return -1;
        return mouseY < rowY(idx) + ROW_H ? idx : -1;
    }

    private boolean inMenuButton(double mouseX) {
        return mouseX >= gridLeft - 6 - MENU_W && mouseX < gridLeft - 6;
    }

    // ---------------------------------------------------------------- Render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        //? if forge {
        /*renderBackground(g);
        *///?}
        super.render(g, mouseX, mouseY, partialTicks);
        refreshVillagers();
        queryIfChanged();
        layoutColumns();
        clampScroll();

        g.drawCenteredString(font, title, width / 2, EDGE + 6, 0xFFFFFFFF);

        int today = todayDow();
        int labelY = gridTop - font.lineHeight - 3;
        headerLabel(g, Component.translatable("townstead.wardrobe.work").getString(), colX(0), cellW, labelY, 0xFFC8C8C8);
        for (int d = 0; d < dayCols; d++) {
            headerLabel(g, weekdayShort(d), colX(d + 1), cellW, labelY, d == today ? ACCENT : 0xFFC8C8C8);
        }
        headerLabel(g, Component.translatable("townstead.wardrobe.warm").getString(), weatherX(true), WEATHER_W, labelY, 0xFFC8C8C8);
        headerLabel(g, Component.translatable("townstead.wardrobe.light").getString(), weatherX(false), WEATHER_W, labelY, 0xFFC8C8C8);

        boolean interactive = menuRow == null;
        renderVillageRow(g, mouseX, mouseY, interactive);

        g.enableScissor(EDGE - 2, rowsTop, width - EDGE + 2, gridBottom);
        for (int idx = 0; idx < villagers.size(); idx++) {
            int y = rowY(idx);
            if (y + ROW_H < rowsTop) continue;
            if (y > gridBottom) break;
            renderRow(g, villagers.get(idx), y, mouseX, mouseY, interactive);
        }
        g.disableScissor();

        if (today >= 0 && today < dayCols) {
            int tx = colX(today + 1);
            g.fill(tx, labelY - 1, tx + cellW - 1, gridBottom, TODAY_COL_TINT);
            g.fill(tx, labelY - 1, tx + 1, gridBottom, TODAY_COL_BORDER);
            g.fill(tx + cellW - 2, labelY - 1, tx + cellW - 1, gridBottom, TODAY_COL_BORDER);
        }

        if (!pasteMode) {
            String hint = Component.translatable("townstead.wardrobe.hint").getString();
            g.drawString(font, font.plainSubstrByWidth(hint, width - EDGE - 76 - EDGE), EDGE + 76,
                    height - EDGE - 20 + 6, 0xFF9098A2, false);
        } else {
            pasteButton.setMessage(Component.translatable("townstead.wardrobe.paste_count", selected.size()));
            pasteButton.active = !selected.isEmpty();
        }

        if (menuRow != null) renderMenu(g, mouseX, mouseY);
        else if (!pasteMode) renderTooltip(g, mouseX, mouseY);
    }

    private void headerLabel(GuiGraphics g, String label, int x, int w, int y, int color) {
        String shown = font.plainSubstrByWidth(label, w - 2);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y, color, false);
    }

    private void renderVillageRow(GuiGraphics g, int mouseX, int mouseY, boolean interactive) {
        int y = gridTop;
        g.fill(EDGE - 2, y - 1, width - EDGE + 2, y + ROW_H, VILLAGE_ROW_TINT);
        g.drawString(font, Component.translatable("townstead.wardrobe.village"), nameLeft,
                y + (ROW_H - font.lineHeight) / 2, ACCENT, false);
        String note = font.plainSubstrByWidth(Component.translatable("townstead.wardrobe.village_note").getString(),
                gridRight - gridLeft - 8);
        g.drawString(font, note, gridLeft + 4, y + (ROW_H - font.lineHeight) / 2, 0xFF9098A2, false);
        for (boolean warm : new boolean[] {true, false}) {
            boolean on = warm ? WardrobeClientStore.villageWarm() : WardrobeClientStore.villageLight();
            renderWeatherCell(g, weatherX(warm), y, on, true, mouseX, mouseY, interactive && !pasteMode, false);
        }
    }

    private void renderRow(GuiGraphics g, UUID uuid, int y, int mouseX, int mouseY, boolean interactive) {
        boolean rowHover = interactive && mouseY >= y && mouseY < y + ROW_H && mouseY >= rowsTop && mouseY < gridBottom;
        if (pasteMode && selected.contains(uuid)) g.fill(EDGE - 2, y - 1, width - EDGE + 2, y + ROW_H, ROW_SELECTED);
        else if (rowHover) g.fill(EDGE - 2, y - 1, width - EDGE + 2, y + ROW_H, ROW_HOVER);

        int textY = y + (ROW_H - font.lineHeight) / 2;
        int nameX = nameLeft;
        if (pasteMode) {
            int cy = y + (ROW_H - 9) / 2;
            border(g, nameX, cy, 9, 9, 0xFFB6BCC4);
            if (selected.contains(uuid)) g.fill(nameX + 2, cy + 2, nameX + 7, cy + 7, ACCENT);
            nameX += CHECK_W;
        }
        boolean locked = locked(uuid);
        int nameRight = gridLeft - 6 - MENU_W - 2 - (locked ? 9 : 0);
        g.drawString(font, font.plainSubstrByWidth(nameOf(uuid), nameRight - nameX), nameX, textY, 0xFFE8E8E8, false);
        if (locked) padlock(g, nameRight + 1, y + (ROW_H - 9) / 2);
        if (rowHover && !pasteMode) {
            int mx = gridLeft - 6 - MENU_W;
            boolean over = inMenuButton(mouseX);
            g.drawString(font, "...", mx + 2, textY, over ? ACCENT : 0xFFB6BCC4, false);
        }

        WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(uuid);
        LivingEntity subject = resident == null ? null : WardrobePreview.subject(uuid, resident.gender());
        for (int col = 0; col <= dayCols; col++) {
            int x = colX(col);
            int cw = cellW - 2;
            int ch = ROW_H - 1;
            String value = col == 0 ? WardrobeClientStore.work(uuid) : WardrobeClientStore.villager(uuid, col - 1);
            renderSkinCell(g, subject, value, col == 0, x, y, cw, ch);
            if (col > 0 && worksOn(uuid, col - 1)) g.fill(x + 1, y + 1, x + 3, y + ch - 1, WORK_MARK);
            if (locked) g.fill(x, y, x + cw, y + ch, LOCKED_DIM);
            border(g, x, y, cw, ch, 0x66000000);
            if (interactive && !pasteMode && !locked && mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + ch
                    && mouseY >= rowsTop && mouseY < gridBottom) {
                g.fill(x, y, x + cw, y + ch, 0x30FFFFFF);
                border(g, x, y, cw, ch, ACCENT);
            }
        }
        for (boolean warm : new boolean[] {true, false}) {
            byte state = resident == null ? WardrobeAssignments.INHERIT : warm ? resident.warm() : resident.light();
            renderWeatherCell(g, weatherX(warm), y, WardrobeClientStore.layersOn(uuid, warm),
                    state != WardrobeAssignments.INHERIT, mouseX, mouseY,
                    interactive && !pasteMode && mouseY >= rowsTop && mouseY < gridBottom, true);
        }
    }

    private void renderSkinCell(GuiGraphics g, @Nullable LivingEntity subject, String value, boolean work,
                                int x, int y, int w, int h) {
        WardrobeTemplate template = WardrobeClientStore.template(value);
        if (value.isEmpty()) {
            g.fill(x, y, x + w, y + h, work ? WORK_FILL : EMPTY_FILL);
            String label = work ? Component.translatable("townstead.wardrobe.job_short").getString() : "–";
            String shown = font.plainSubstrByWidth(label, w - 4);
            g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - font.lineHeight) / 2 + 1,
                    0xFF6A7078, false);
            return;
        }
        g.fill(x, y, x + w, y + h, CELL_FILL);
        if (template != null) {
            String shown = font.plainSubstrByWidth(templateName(template), w - 4);
            g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - font.lineHeight) / 2 + 1,
                    0xFFFFFFFF, false);
            return;
        }
        if (subject != null) {
            WardrobePreview.bust(g, subject, value, x + 1, y + 1, x + w - 1, y + h - 1);
        } else {
            String shown = font.plainSubstrByWidth(WardrobePreview.label(value), w - 4);
            g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - font.lineHeight) / 2 + 1,
                    0xFFFFFFFF, false);
        }
    }

    private void renderWeatherCell(GuiGraphics g, int x, int y, boolean on, boolean own, int mouseX, int mouseY,
                                   boolean interactive, boolean villagerRow) {
        int w = WEATHER_W - 2;
        int h = ROW_H - 1;
        g.fill(x, y, x + w, y + h, own ? CELL_FILL : EMPTY_FILL);
        String label = Component.translatable(on ? "townstead.wardrobe.on" : "townstead.wardrobe.off").getString();
        int color = !own ? 0xFF7A808A : on ? 0xFF9FE08A : 0xFFE08A8A;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2 + 1, color, false);
        border(g, x, y, w, h, 0x66000000);
        if (interactive && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            g.fill(x, y, x + w, y + h, 0x30FFFFFF);
            border(g, x, y, w, h, ACCENT);
        }
    }

    private void padlock(GuiGraphics g, int x, int y) {
        int c = 0xFFB6BCC4;
        g.fill(x + 2, y, x + 6, y + 1, c);
        g.fill(x + 1, y + 1, x + 2, y + 4, c);
        g.fill(x + 6, y + 1, x + 7, y + 4, c);
        g.fill(x, y + 4, x + 8, y + 9, c);
        g.fill(x + 3, y + 5, x + 5, y + 7, 0xFF1B1F26);
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

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (mouseY >= gridTop && mouseY < gridTop + ROW_H) {
            for (boolean warm : new boolean[] {true, false}) {
                int x = weatherX(warm);
                if (mouseX >= x && mouseX < x + WEATHER_W - 2) {
                    lines.add(Component.translatable(warm ? "townstead.wardrobe.warm_desc" : "townstead.wardrobe.light_desc"));
                    lines.add(Component.translatable("townstead.wardrobe.village_toggle").withStyle(ChatFormatting.GRAY));
                }
            }
            if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        int idx = rowAt(mouseY);
        if (idx < 0) return;
        UUID uuid = villagers.get(idx);
        if (mouseX < gridLeft) {
            if (inMenuButton(mouseX)) lines.add(Component.translatable("townstead.wardrobe.row_tools"));
            else if (locked(uuid)) lines.add(Component.translatable("townstead.wardrobe.locked"));
        } else if (mouseX >= weatherLeft) {
            boolean warm = mouseX < weatherLeft + WEATHER_W;
            WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(uuid);
            byte state = resident == null ? WardrobeAssignments.INHERIT : warm ? resident.warm() : resident.light();
            lines.add(Component.translatable(warm ? "townstead.wardrobe.warm_desc" : "townstead.wardrobe.light_desc"));
            lines.add(Component.translatable(state == WardrobeAssignments.INHERIT ? "townstead.wardrobe.follows_village"
                    : "townstead.wardrobe.own_setting").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("townstead.wardrobe.weather_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (locked(uuid)) {
            lines.add(Component.translatable("townstead.wardrobe.locked"));
        } else if (mouseX < gridLeft + (dayCols + 1) * cellW) {
            int col = (int) ((mouseX - gridLeft) / cellW);
            String value = col == 0 ? WardrobeClientStore.work(uuid) : WardrobeClientStore.villager(uuid, col - 1);
            lines.add(Component.literal(col == 0 ? Component.translatable("townstead.wardrobe.work").getString()
                    : weekdayLong(col - 1)).withStyle(ChatFormatting.GRAY));
            WardrobeTemplate template = WardrobeClientStore.template(value);
            if (value.isEmpty()) {
                lines.add(Component.translatable(col == 0 ? "townstead.wardrobe.profession_clothes"
                        : "townstead.wardrobe.own_choice"));
                if (col > 0) lines.add(Component.translatable("townstead.wardrobe.own_choice_desc")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.literal(template != null ? templateName(template) : WardrobePreview.label(value)));
                lines.add(Component.translatable("townstead.wardrobe.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (col > 0 && worksOn(uuid, col - 1)) {
                lines.add(Component.translatable("townstead.wardrobe.work_day").withStyle(ChatFormatting.GRAY));
            }
        }
        if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    static String templateName(WardrobeTemplate template) {
        if (I18n.exists(template.nameKey())) return I18n.get(template.nameKey());
        return template.fallbackName();
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ---------------------------------------------------------------- Row tools

    private List<MenuItem> menuItems() {
        return List.of(MenuItem.FILL, MenuItem.COPY, MenuItem.PASTE, MenuItem.PASTE_MANY, MenuItem.CLEAR);
    }

    private boolean menuEnabled(MenuItem item) {
        if (menuRow != null && locked(menuRow) && item != MenuItem.COPY) return false;
        return item != MenuItem.PASTE && item != MenuItem.PASTE_MANY || clipboard != null;
    }

    private int menuW() {
        int w = 0;
        for (MenuItem item : menuItems()) w = Math.max(w, font.width(menuLabel(item)));
        return w + 12;
    }

    private Component menuLabel(MenuItem item) {
        return Component.translatable("townstead.wardrobe.menu." + item.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void renderMenu(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        List<MenuItem> items = menuItems();
        int w = menuW();
        int h = items.size() * MENU_ROW_H + 4;
        g.fill(menuX, menuY, menuX + w, menuY + h, MENU_BG);
        border(g, menuX, menuY, w, h, MENU_BORDER);
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            int y = menuY + 2 + i * MENU_ROW_H;
            boolean enabled = menuEnabled(item);
            boolean hover = enabled && mouseX >= menuX && mouseX < menuX + w && mouseY >= y && mouseY < y + MENU_ROW_H;
            if (hover) g.fill(menuX + 1, y, menuX + w - 1, y + MENU_ROW_H, 0x40FFFFFF);
            g.drawString(font, menuLabel(item), menuX + 6, y + 3, enabled ? 0xFFFFFFFF : 0xFF6A7078, false);
        }
        g.pose().popPose();
    }

    private boolean clickMenu(double mouseX, double mouseY) {
        UUID row = menuRow;
        List<MenuItem> items = menuItems();
        int w = menuW();
        menuRow = null;
        if (row == null || mouseX < menuX || mouseX >= menuX + w) return false;
        int i = (int) ((mouseY - menuY - 2) / MENU_ROW_H);
        if (mouseY < menuY + 2 || i < 0 || i >= items.size()) return false;
        MenuItem item = items.get(i);
        if (!menuEnabled(item)) return true;
        switch (item) {
            case FILL -> openPicker(row, 0, true);
            case COPY -> {
                WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(row);
                clipboard = resident == null ? List.of() : List.copyOf(resident.row());
            }
            case PASTE -> send(WardrobeAssignPayload.paste(List.of(row), clipboard));
            case PASTE_MANY -> {
                setPasteMode(true);
                selected.add(row);
            }
            case CLEAR -> send(WardrobeAssignPayload.paste(List.of(row), List.of()));
        }
        return true;
    }

    private void setPasteMode(boolean on) {
        pasteMode = on;
        selected.clear();
        applyPasteMode();
    }

    private void applyPasteMode() {
        householdButton.visible = worksiteButton.visible = pasteButton.visible = cancelButton.visible = pasteMode;
    }

    /** Adds everyone who shares a home (or a workplace) with a selected villager. */
    private void selectShared(boolean household) {
        Set<String> keys = new LinkedHashSet<>();
        for (UUID uuid : selected) {
            String key = sharedKey(uuid, household);
            if (!key.isEmpty()) keys.add(key);
        }
        if (keys.isEmpty()) return;
        for (UUID uuid : villagers) {
            if (keys.contains(sharedKey(uuid, household)) && !locked(uuid)) selected.add(uuid);
        }
    }

    private String sharedKey(UUID uuid, boolean household) {
        WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(uuid);
        if (resident == null) return "";
        return household ? resident.household() : resident.worksite();
    }

    private void pasteSelected() {
        if (clipboard != null && !selected.isEmpty()) send(WardrobeAssignPayload.paste(List.copyOf(selected), clipboard));
        setPasteMode(false);
    }

    // ---------------------------------------------------------------- Input

    private void openPicker(UUID uuid, int col, boolean allDays) {
        if (minecraft == null || minecraft.player == null) return;
        int day = col == 0 && !allDays ? WardrobeClothingScreen.WORK : Math.max(0, col - 1);
        Component heading = day == WardrobeClothingScreen.WORK
                ? Component.translatable("townstead.wardrobe.assign_work", nameOf(uuid))
                : Component.translatable("townstead.wardrobe.assign_day", nameOf(uuid),
                        allDays ? Component.translatable("townstead.wardrobe.every_day").getString() : weekdayLong(day));
        minecraft.setScreen(new WardrobeClothingScreen(this, uuid, minecraft.player.getUUID(), heading, day, allDays));
    }

    private static byte nextState(byte state) {
        return state == WardrobeAssignments.INHERIT ? WardrobeAssignments.ON
                : state == WardrobeAssignments.ON ? WardrobeAssignments.OFF : WardrobeAssignments.INHERIT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuRow != null) {
            clickMenu(mouseX, mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (!pasteMode && mouseY >= gridTop && mouseY < gridTop + ROW_H) {
            for (boolean warm : new boolean[] {true, false}) {
                int x = weatherX(warm);
                if (mouseX >= x && mouseX < x + WEATHER_W - 2) {
                    boolean on = warm ? WardrobeClientStore.villageWarm() : WardrobeClientStore.villageLight();
                    send(WardrobeAssignPayload.weather(null, warm, on ? WardrobeAssignments.OFF : WardrobeAssignments.ON));
                    return true;
                }
            }
            return false;
        }

        int idx = rowAt(mouseY);
        if (idx < 0) return false;
        UUID uuid = villagers.get(idx);
        if (pasteMode) {
            if (!locked(uuid) && !selected.remove(uuid)) selected.add(uuid);
            return true;
        }
        if (mouseX < gridLeft) {
            if (inMenuButton(mouseX) || button == 1) {
                menuRow = uuid;
                menuX = (int) Math.min(mouseX, width - menuW() - 4);
                menuY = (int) Math.min(mouseY, height - menuItems().size() * MENU_ROW_H - 8);
            }
            return true;
        }
        if (mouseX >= weatherLeft) {
            boolean warm = mouseX < weatherLeft + WEATHER_W;
            WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(uuid);
            byte state = resident == null ? WardrobeAssignments.INHERIT : warm ? resident.warm() : resident.light();
            send(WardrobeAssignPayload.weather(uuid, warm, button == 1 ? WardrobeAssignments.INHERIT : nextState(state)));
            return true;
        }
        if (locked(uuid) || mouseX >= gridLeft + (dayCols + 1) * cellW) return true;
        int col = (int) ((mouseX - gridLeft) / cellW);
        if (button == 1) {
            if (col == 0) send(WardrobeAssignPayload.work(uuid, ""));
            else send(WardrobeAssignPayload.cell(uuid, col - 1, ""));
        } else if (button == 0) {
            openPicker(uuid, col, false);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 //? if >=1.21 {
                                 double scrollX, double scrollY) {
                                 //?} else {
                                 /*double scrollY) {
                                 *///?}
        menuRow = null;
        rowScroll -= (int) (scrollY * (ROW_H + CELL_GAP));
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int content = villagers.size() * (ROW_H + CELL_GAP);
        int max = Math.max(0, content - (gridBottom - rowsTop));
        rowScroll = Math.max(0, Math.min(max, rowScroll));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && (menuRow != null || pasteMode)) {
            menuRow = null;
            if (pasteMode) setPasteMode(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    //? if neoforge {
    static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
    //?} else {
    /*static void send(WardrobeAssignPayload payload) {
        TownsteadNetwork.sendToServer(payload);
    }
    *///?}

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(returnScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
