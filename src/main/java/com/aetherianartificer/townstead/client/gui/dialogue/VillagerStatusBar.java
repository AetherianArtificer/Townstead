package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders hunger/thirst/fatigue status icons for a villager.
 * Reusable across different screens.
 */
public class VillagerStatusBar {
    //? if >=1.21 {
    private static final ResourceLocation FOOD_FULL = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_full.png");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_half.png");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_empty.png");
    private static final ResourceLocation ENERGY_FULL = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_full.png");
    private static final ResourceLocation ENERGY_THREE_QUARTER = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_three_quarter.png");
    private static final ResourceLocation ENERGY_HALF = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_half.png");
    private static final ResourceLocation ENERGY_QUARTER = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_quarter.png");
    private static final ResourceLocation ENERGY_EMPTY = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_empty.png");
    //?} else {
    /*private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation ENERGY_FULL = new ResourceLocation("townstead", "textures/gui/energy_full.png");
    private static final ResourceLocation ENERGY_THREE_QUARTER = new ResourceLocation("townstead", "textures/gui/energy_three_quarter.png");
    private static final ResourceLocation ENERGY_HALF = new ResourceLocation("townstead", "textures/gui/energy_half.png");
    private static final ResourceLocation ENERGY_QUARTER = new ResourceLocation("townstead", "textures/gui/energy_quarter.png");
    private static final ResourceLocation ENERGY_EMPTY = new ResourceLocation("townstead", "textures/gui/energy_empty.png");
    *///?}

    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 20;
    private static final float ICON_SCALE = 16.0f / 9.0f;

    private final int entityId;

    public VillagerStatusBar(int entityId) {
        this.entityId = entityId;
    }

    public void render(GuiGraphics graphics, int baseX, int baseY) {
        int iconX = baseX;

        // Hunger icon (conditional)
        if (TownsteadConfig.isVillagerHungerEnabled()) {
            renderHungerIcon(graphics, iconX, baseY);
            iconX += ICON_SPACING;
        }

        // Thirst icon (conditional)
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge != null && TownsteadConfig.isVillagerThirstEnabled()) {
            renderThirstIcon(graphics, iconX, baseY, bridge);
            iconX += ICON_SPACING;
        }

        // Energy icon (conditional)
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            renderEnergyIcon(graphics, iconX, baseY);
        }
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY, int baseX, int baseY) {
        int iconX = baseX;

        if (TownsteadConfig.isVillagerHungerEnabled()) {
            if (isHovering(mouseX, mouseY, iconX, baseY)) {
                int hunger = HungerClientStore.get(entityId);
                HungerData.HungerState state = HungerData.getState(hunger);
                Component label = Component.translatable("townstead.hunger.icon.tooltip",
                                Component.translatable(state.getTranslationKey()), hunger)
                        .withStyle(Style.EMPTY.withColor(state.getColor()));
                graphics.renderTooltip(font, label, mouseX, mouseY);
                return;
            }
            iconX += ICON_SPACING;
        }

        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge != null && TownsteadConfig.isVillagerThirstEnabled()) {
            if (isHovering(mouseX, mouseY, iconX, baseY)) {
                int thirst = ThirstClientStore.getThirst(entityId);
                ThirstData.ThirstState thirstState = ThirstData.getState(thirst);
                Component label = Component.translatable("townstead.thirst.icon.tooltip", thirst)
                        .withStyle(Style.EMPTY.withColor(thirstState.getColor()));
                graphics.renderTooltip(font, label, mouseX, mouseY);
                return;
            }
            iconX += ICON_SPACING;
        }

        if (TownsteadConfig.isVillagerFatigueEnabled() && isHovering(mouseX, mouseY, iconX, baseY)) {
            int fatigue = FatigueClientStore.getFatigue(entityId);
            int energy = FatigueData.toEnergy(fatigue);
            FatigueData.FatigueState fatigueState = FatigueData.getState(fatigue);
            Component label = Component.translatable("townstead.energy.icon.tooltip",
                            Component.translatable(fatigueState.getTranslationKey()), energy)
                    .withStyle(Style.EMPTY.withColor(fatigueState.getColor()));
            graphics.renderTooltip(font, label, mouseX, mouseY);
        }
    }

    private void renderHungerIcon(GuiGraphics graphics, int x, int y) {
        int hunger = HungerClientStore.get(entityId);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(ICON_SCALE, ICON_SCALE, 1.0f);
        //? if >=1.21 {
        ResourceLocation sprite = hungerSprite(HungerData.getState(hunger));
        graphics.blit(sprite, 0, 0, 0, 0, 9, 9, 9, 9);
        //?} else {
        /*int u = hungerU(HungerData.getState(hunger));
        graphics.blit(ICONS, 0, 0, u, 27, 9, 9, 256, 256);
        *///?}
        pose.popPose();
    }

    private void renderThirstIcon(GuiGraphics graphics, int x, int y, ThirstCompatBridge bridge) {
        int thirst = ThirstClientStore.getThirst(entityId);
        ThirstCompatBridge.ThirstIconInfo icon = bridge.iconInfo(thirst);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(ICON_SCALE, ICON_SCALE, 1.0f);
        graphics.blit(icon.texture(), 0, 0, icon.u(), icon.v(), 9, 9, icon.texW(), icon.texH());
        pose.popPose();
    }

    private void renderEnergyIcon(GuiGraphics graphics, int x, int y) {
        int fatigue = FatigueClientStore.getFatigue(entityId);
        FatigueData.FatigueState state = FatigueData.getState(fatigue);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(ICON_SCALE, ICON_SCALE, 1.0f);
        graphics.blit(energySprite(state), 0, 0, 0, 0, 9, 9, 9, 9);
        pose.popPose();
    }

    private boolean isHovering(int mouseX, int mouseY, int iconX, int iconY) {
        return mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                && mouseY >= iconY && mouseY <= iconY + ICON_SIZE;
    }

    //? if >=1.21 {
    private static ResourceLocation hungerSprite(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> FOOD_FULL;
            case HUNGRY -> FOOD_HALF;
            case FAMISHED, STARVING -> FOOD_EMPTY;
        };
    }
    //?} else {
    /*private static int hungerU(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> 52;
            case HUNGRY -> 61;
            case FAMISHED, STARVING -> 16;
        };
    }
    *///?}

    private static ResourceLocation energySprite(FatigueData.FatigueState state) {
        return switch (state) {
            case RESTED -> ENERGY_FULL;
            case ALERT -> ENERGY_THREE_QUARTER;
            case TIRED -> ENERGY_HALF;
            case DROWSY -> ENERGY_QUARTER;
            case EXHAUSTED -> ENERGY_EMPTY;
        };
    }
}
