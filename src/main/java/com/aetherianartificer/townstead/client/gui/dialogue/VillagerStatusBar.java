package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.client.root.ClientNeeds;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.temperature.TemperatureClientStore;
import com.aetherianartificer.townstead.temperature.TemperatureData;
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
    // Icons live in the townstead_icons namespace (assets/townstead_icons/*.png).
    //? if >=1.21 {
    private static final ResourceLocation HUNGER_FULL = ResourceLocation.fromNamespaceAndPath("townstead_icons", "hunger_full.png");
    private static final ResourceLocation HUNGER_HALF = ResourceLocation.fromNamespaceAndPath("townstead_icons", "hunger_half.png");
    private static final ResourceLocation HUNGER_LOW = ResourceLocation.fromNamespaceAndPath("townstead_icons", "hunger_low.png");
    private static final ResourceLocation ENERGY_FULL = ResourceLocation.fromNamespaceAndPath("townstead_icons", "energy_full.png");
    private static final ResourceLocation ENERGY_HALF = ResourceLocation.fromNamespaceAndPath("townstead_icons", "energy_half.png");
    private static final ResourceLocation ENERGY_LOW = ResourceLocation.fromNamespaceAndPath("townstead_icons", "energy_low.png");
    //?} else {
    /*private static final ResourceLocation HUNGER_FULL = new ResourceLocation("townstead_icons", "hunger_full.png");
    private static final ResourceLocation HUNGER_HALF = new ResourceLocation("townstead_icons", "hunger_half.png");
    private static final ResourceLocation HUNGER_LOW = new ResourceLocation("townstead_icons", "hunger_low.png");
    private static final ResourceLocation ENERGY_FULL = new ResourceLocation("townstead_icons", "energy_full.png");
    private static final ResourceLocation ENERGY_HALF = new ResourceLocation("townstead_icons", "energy_half.png");
    private static final ResourceLocation ENERGY_LOW = new ResourceLocation("townstead_icons", "energy_low.png");
    *///?}

    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 20;
    // Thirst still uses a 9px bridge sprite; hunger/energy are native 16px.
    private static final float THIRST_ICON_SCALE = 16.0f / 9.0f;

    private final int entityId;

    public VillagerStatusBar(int entityId) {
        this.entityId = entityId;
    }

    public void render(GuiGraphics graphics, int baseX, int baseY) {
        int iconX = baseX;

        // Hunger icon (conditional; a suppress-need gene hides it for this villager)
        if (TownsteadConfig.isVillagerHungerEnabled() && !ClientNeeds.suppresses(entityId, "hunger")) {
            renderHungerIcon(graphics, iconX, baseY);
            iconX += ICON_SPACING;
        }

        // Thirst icon (conditional)
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge != null && TownsteadConfig.isVillagerThirstEnabled() && !ClientNeeds.suppresses(entityId, "thirst")) {
            renderThirstIcon(graphics, iconX, baseY, bridge);
            iconX += ICON_SPACING;
        }

        // Energy icon (conditional)
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            renderEnergyIcon(graphics, iconX, baseY);
            iconX += ICON_SPACING;
        }

        if (temperatureShown()) {
            com.aetherianartificer.townstead.client.gui.TemperatureIcons.draw(
                    graphics, TemperatureClientStore.getTier(entityId), iconX, baseY, ICON_SIZE);
        }
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY, int baseX, int baseY) {
        int iconX = baseX;

        if (TownsteadConfig.isVillagerHungerEnabled() && !ClientNeeds.suppresses(entityId, "hunger")) {
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
        if (bridge != null && TownsteadConfig.isVillagerThirstEnabled() && !ClientNeeds.suppresses(entityId, "thirst")) {
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
            return;
        }
        if (TownsteadConfig.isVillagerFatigueEnabled()) iconX += ICON_SPACING;

        if (temperatureShown() && isHovering(mouseX, mouseY, iconX, baseY)) {
            boolean fahrenheit = TownsteadConfig.temperatureInFahrenheit();
            TemperatureData.Tier tier = TemperatureClientStore.getTier(entityId);
            Component label = TemperatureClientStore.tooltip(entityId, fahrenheit);
            graphics.renderTooltip(font, label, mouseX, mouseY);
        }
    }

    private boolean temperatureShown() {
        return TownsteadConfig.isVillagerTemperatureEnabled() && !ClientNeeds.suppresses(entityId, "temperature");
    }


    private void renderHungerIcon(GuiGraphics graphics, int x, int y) {
        int hunger = HungerClientStore.get(entityId);
        drawIcon(graphics, hungerSprite(HungerData.getState(hunger)), x, y);
    }

    // Icon art is a glyph-tight 12px sprite; scale it to the ICON_SIZE footprint.
    private void drawIcon(GuiGraphics graphics, ResourceLocation sprite, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(ICON_SIZE / 12.0f, ICON_SIZE / 12.0f, 1.0f);
        graphics.blit(sprite, 0, 0, 0, 0, 12, 12, 12, 12);
        pose.popPose();
    }

    private void renderThirstIcon(GuiGraphics graphics, int x, int y, ThirstCompatBridge bridge) {
        int thirst = ThirstClientStore.getThirst(entityId);
        ThirstCompatBridge.ThirstIconInfo icon = bridge.iconInfo(thirst);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(THIRST_ICON_SCALE, THIRST_ICON_SCALE, 1.0f);
        graphics.blit(icon.texture(), 0, 0, icon.u(), icon.v(), 9, 9, icon.texW(), icon.texH());
        pose.popPose();
    }

    private void renderEnergyIcon(GuiGraphics graphics, int x, int y) {
        int fatigue = FatigueClientStore.getFatigue(entityId);
        FatigueData.FatigueState state = FatigueData.getState(fatigue);
        drawIcon(graphics, energySprite(state), x, y);
    }

    private boolean isHovering(int mouseX, int mouseY, int iconX, int iconY) {
        return mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                && mouseY >= iconY && mouseY <= iconY + ICON_SIZE;
    }

    private static ResourceLocation hungerSprite(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> HUNGER_FULL;
            case HUNGRY -> HUNGER_HALF;
            case FAMISHED, STARVING -> HUNGER_LOW;
        };
    }

    private static ResourceLocation energySprite(FatigueData.FatigueState state) {
        return switch (state) {
            case RESTED, ALERT -> ENERGY_FULL;
            case TIRED -> ENERGY_HALF;
            case DROWSY, EXHAUSTED -> ENERGY_LOW;
        };
    }
}
