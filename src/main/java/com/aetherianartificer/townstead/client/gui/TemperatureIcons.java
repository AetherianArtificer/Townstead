package com.aetherianartificer.townstead.client.gui;

import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Backend artwork, driven by the villager's body tier rather than the player's temperature. */
public final class TemperatureIcons {
    private TemperatureIcons() {}

    public record Icon(ResourceLocation texture, int u, int v, int size, int width, int height, int overlayU) {}

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    public static Icon icon(String backend, TemperatureData.Tier tier) {
        int direction = tier.isCold() ? -1 : 1;
        int severity = tier.severity();
        return switch (backend) {
            case "cold_sweat" -> new Icon(id("cold_sweat", "textures/gui/overlay/body_temp_gauge.png"),
                    0, 40 - direction * (severity == 3 ? 4 : severity) * 10, 10, 10, 90, -1);
            case "legendary_survival_overhaul" -> new Icon(id("legendarysurvivaloverhaul", "textures/gui/overlay.png"),
                    severity == 3 ? (tier.isCold() ? 32 : 16) : 0, 48, 16, 256, 256,
                    16 * (severity == 0 ? 3 : severity == 3 ? (tier.isCold() ? 5 : 4) : (tier.isCold() ? 12 : 11)));
            case "tough_as_nails" -> new Icon(id("toughasnails", "textures/gui/icons.png"),
                    16 * (2 + direction * Math.min(2, severity)), 0, 16, 256, 256, -1);
            default -> new Icon(id("townstead_icons", "temperature_"
                    + (severity < 2 ? "ok" : tier.isCold() ? "cold" : "hot") + ".png"), 0, 0, 12, 12, 12, -1);
        };
    }

    public static void draw(GuiGraphics graphics, TemperatureData.Tier tier, int x, int y, int size) {
        Icon icon = icon(TemperatureBridgeResolver.get().id(), tier);
        if (Minecraft.getInstance().getResourceManager().getResource(icon.texture()).isEmpty()) {
            icon = icon("builtin", tier);
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / (float) icon.size(), size / (float) icon.size(), 1);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        graphics.blit(icon.texture(), 0, 0, icon.u(), icon.v(), icon.size(), icon.size(), icon.width(), icon.height());
        if (icon.overlayU() >= 0) {
            graphics.blit(icon.texture(), 0, 0, icon.overlayU(), icon.v(), icon.size(), icon.size(), icon.width(), icon.height());
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        pose.popPose();
    }
}
