package com.aetherianartificer.townstead.client.gui.wardrobe;

import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeAssignPayload;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeClientStore;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeSyncPayload;
import com.aetherianartificer.townstead.mixin.accessor.VillagerEditorClothingAccessor;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MCA's own clothing selector, opened for a Wardrobe cell. The pick goes to the Wardrobe instead
 * of the villager, and nothing is written through the editor. Right-click stars a favourite.
 */
public class WardrobeClothingScreen extends VillagerEditorScreen {

    /** The day this edits, or {@link #WORK} for the Work outfit. */
    static final int WORK = -10;

    private final Screen parent;
    private final UUID target;
    private final Component heading;
    private final int day;
    private boolean allDays;
    private boolean leaving;
    private Button allDaysButton;

    WardrobeClothingScreen(Screen parent, UUID villager, UUID player, Component heading, int day, boolean allDays) {
        super(villager, player);
        this.parent = parent;
        this.target = villager;
        this.heading = heading;
        this.day = day;
        this.allDays = allDays;
    }

    @Override
    protected boolean shouldShowPageSelection() {
        return false;
    }

    @Override
    public void syncVillagerData() {
    }

    @Override
    protected void setPage(String page) {
        if (leaving) return;
        if ("clothing_style".equals(page)) {
            leaving = true;
            return;
        }
        if ("general".equals(page)) page = "clothing";
        super.setPage(page);
        if ("clothing".equals(page)) addWardrobeButtons();
    }

    private void addWardrobeButtons() {
        int y = height / 2 + 108;
        if (day != WORK) {
            allDaysButton = addRenderableWidget(Button.builder(allDaysLabel(), b -> {
                allDays = !allDays;
                b.setMessage(allDaysLabel());
            }).bounds(width / 2 - 140, y, 120, 20).build());
        }
        Component clear = Component.translatable(day == WORK ? "townstead.wardrobe.profession_clothes"
                : "townstead.wardrobe.own_choice");
        addRenderableWidget(Button.builder(clear, b -> {
            send("");
            leaving = true;
        }).bounds(width / 2 + 20, y, 120, 20).build());
    }

    private Component allDaysLabel() {
        return Component.translatable(allDays ? "townstead.wardrobe.all_days_on" : "townstead.wardrobe.all_days");
    }

    @Override
    protected void eventCallback(String event) {
        if ("clothing".equals(event)) send(villager.getClothes());
    }

    private void send(String skin) {
        if (day == WORK) WardrobeScreen.send(WardrobeAssignPayload.work(target, skin));
        else WardrobeScreen.send(WardrobeAssignPayload.cell(target, allDays ? WardrobeAssignPayload.ALL_DAYS : day, skin));
    }

    private String hovered() {
        VillagerEditorClothingAccessor accessor = (VillagerEditorClothingAccessor) this;
        int index = accessor.townstead$getHoveredClothingId();
        List<String> list = accessor.townstead$getFilteredClothing();
        return "clothing".equals(page) && index >= 0 && list != null && index < list.size() ? list.get(index) : null;
    }

    private boolean starred(String skin) {
        WardrobeSyncPayload.Resident resident = WardrobeClientStore.resident(target);
        return resident != null && resident.starred().contains(skin);
    }

    @Override
    public void tick() {
        super.tick();
        if (leaving && minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);
        if (!"clothing".equals(page)) return;
        g.drawCenteredString(font, heading, width / 2, height / 2 - 122, 0xFFFFFFFF);
        String skin = hovered();
        if (skin == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal((starred(skin) ? "★ " : "") + WardrobePreview.label(skin)));
        lines.add(Component.translatable(starred(skin) ? "townstead.wardrobe.unstar_hint"
                : "townstead.wardrobe.star_hint").withStyle(ChatFormatting.GRAY));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            String skin = hovered();
            if (skin != null) {
                WardrobeScreen.send(WardrobeAssignPayload.star(target, skin, !starred(skin)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
