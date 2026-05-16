package com.aetherianartificer.townstead.client.gui.shift;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ShiftManagerScreen extends Screen {

    private static final int EDGE = 24;
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 44;
    private static final int NAME_W = 80;
    private static final int CELL_H = 16;
    private static final int CELL_GAP = 2;

    private final Screen returnScreen;

    private int gridLeft;
    private int gridRight;
    private int cellW;
    private int gridTop;
    private int rowsPerPage = 1;
    private int legendY;
    private int legendStep;

    private Button prevPageButton;
    private Button nextPageButton;

    private int shiftPage = 0;
    private List<UUID> shiftVillagerUuids = List.of();
    private final Map<UUID, String> shiftVillagerNames = new HashMap<>();
    private final Map<UUID, int[]> shiftEdits = new HashMap<>();
    private boolean shiftQueried = false;
    private int shiftPaintOrdinal = -1; // -1 = cycle mode, 0-3 = paint mode

    public ShiftManagerScreen(Screen returnScreen) {
        super(Component.translatable("townstead.shift.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();

        gridLeft = EDGE + NAME_W + 6;
        gridRight = width - EDGE;
        cellW = Math.max(8, (gridRight - gridLeft) / ShiftData.HOURS_PER_DAY);
        gridTop = EDGE + HEADER_H;

        int availableH = height - gridTop - FOOTER_H - EDGE;
        rowsPerPage = Math.max(1, availableH / (CELL_H + CELL_GAP));

        refreshShiftVillagers();

        int headerY = EDGE;
        int footerBtnY = height - EDGE - 20;

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

        legendY = footerBtnY - 18;
        legendStep = Math.max(60, (width - EDGE - 70 - (EDGE + 60) - 8) / ShiftData.ORDINAL_COLORS.length);

        queryShiftData();
        updatePageButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);

        refreshShiftVillagers();
        updatePageButtons();

        g.drawCenteredString(this.font, getTitle(), width / 2, EDGE + 6, 0xFFFFFFFF);

        int totalPages = totalPages();
        String pageText = String.format("%d/%d", shiftPage + 1, totalPages);
        g.drawString(this.font, Component.literal(pageText),
                gridRight - 44 - this.font.width(pageText) - 6, EDGE + 6, 0xFFA0A0A0, false);

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
            String name = shiftVillagerNames.getOrDefault(uuid, "???");
            int rowY = gridTop + row * (CELL_H + CELL_GAP);

            String truncated = name;
            while (this.font.width(truncated) > NAME_W - 2 && truncated.length() > 1) {
                truncated = truncated.substring(0, truncated.length() - 1);
            }
            if (!truncated.equals(name)) truncated += "..";

            g.drawString(this.font, truncated,
                    EDGE, rowY + (CELL_H - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);

            int[] shifts = shiftEdits.containsKey(uuid) ? shiftEdits.get(uuid) : ShiftClientStore.get(uuid);

            for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
                int cellX = gridLeft + h * cellW;
                int cellY = rowY;
                int ord = shifts[h];
                if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;

                int color = ShiftData.ORDINAL_COLORS[ord];
                g.fill(cellX, cellY, cellX + cellW - 1, cellY + CELL_H - 1, color);

                if (mouseX >= cellX && mouseX < cellX + cellW - 1
                        && mouseY >= cellY && mouseY < cellY + CELL_H - 1) {
                    g.fill(cellX, cellY, cellX + cellW - 1, cellY + CELL_H - 1, 0x40FFFFFF);
                }
            }
        }

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

        for (int row = 0; row < endIdx - startIdx; row++) {
            int rowY = gridTop + row * (CELL_H + CELL_GAP);
            if (mouseX >= EDGE && mouseX < gridLeft && mouseY >= rowY && mouseY < rowY + CELL_H) {
                UUID uuid = shiftVillagerUuids.get(startIdx + row);
                VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(uuid);
                if (resident != null) {
                    String profName = profDisplayName(resident.professionId());
                    int level = resident.professionLevel();
                    String levelKey = "townstead.profession.level." + Math.min(Math.max(level, 1), 5);
                    String levelName = Component.translatable(levelKey).getString();
                    g.renderTooltip(this.font,
                            Component.literal(profName + " - " + levelName),
                            mouseX, mouseY);
                }
                break;
            }
        }

        int totalGridW = ShiftData.HOURS_PER_DAY * cellW;
        if (mouseX >= gridLeft && mouseX < gridLeft + totalGridW) {
            int h = (int) ((mouseX - gridLeft) / cellW);
            if (h >= 0 && h < ShiftData.HOURS_PER_DAY) {
                int hoveredRow = -1;
                for (int row = 0; row < endIdx - startIdx; row++) {
                    int rowY = gridTop + row * (CELL_H + CELL_GAP);
                    if (mouseY >= rowY && mouseY < rowY + CELL_H) {
                        hoveredRow = row;
                        break;
                    }
                }
                if (hoveredRow >= 0) {
                    UUID uuid = shiftVillagerUuids.get(startIdx + hoveredRow);
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
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseY >= legendY - 2 && mouseY <= legendY + 11) {
                for (int i = 0; i < ShiftData.ORDINAL_COLORS.length; i++) {
                    int lx = EDGE + 70 + i * legendStep;
                    if (mouseX >= lx - 2 && mouseX <= lx + 40) {
                        shiftPaintOrdinal = (shiftPaintOrdinal == i) ? -1 : i;
                        return true;
                    }
                }
            }
            if (applyCell(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
        int gridBottom = gridTop + rowsPerPage * (CELL_H + CELL_GAP);
        if (mouseX >= gridLeft && mouseX <= gridLeft + ShiftData.HOURS_PER_DAY * cellW
                && mouseY >= gridTop && mouseY <= gridBottom) {
            if (scrollY < 0) {
                pageDelta(1);
                return true;
            } else if (scrollY > 0) {
                pageDelta(-1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY,
                //? if >=1.21 {
                scrollX, scrollY);
                //?} else {
                /*scrollY);
                *///?}
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(returnScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshShiftVillagers() {
        shiftVillagerUuids = new ArrayList<>();
        shiftVillagerNames.clear();

        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            UUID uuid = resident.villagerUuid();
            shiftVillagerUuids.add(uuid);
            shiftVillagerNames.put(uuid, resident.name());
            ShiftClientStore.set(uuid, resident.shifts());
        }

        shiftVillagerUuids.sort(Comparator.comparing(
                uuid -> shiftVillagerNames.getOrDefault(uuid, uuid.toString())));
        shiftPage = Math.max(0, Math.min(shiftPage, totalPages() - 1));
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
    }

    private void resetAllShifts() {
        int[] defaults = ShiftData.getVanillaDefault();
        for (UUID uuid : shiftVillagerUuids) {
            shiftEdits.put(uuid, Arrays.copyOf(defaults, defaults.length));
            //? if neoforge {
            PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            *///?}
        }
    }

    private boolean applyCell(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + ShiftData.HOURS_PER_DAY * cellW)
            return false;

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
