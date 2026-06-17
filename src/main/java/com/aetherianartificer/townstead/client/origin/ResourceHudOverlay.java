package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.ability.ResourceSyncS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Draws the local player's resource meters as stacked bars in the top-left, one per
 * {@code resource} gene, filled by value and tinted by the gene's colour. Shared by
 * both versions' HUD registration (NeoForge gui layer / Forge gui overlay).
 */
public final class ResourceHudOverlay {

    private static final int X = 4;
    private static final int TOP = 4;
    private static final int WIDTH = 80;
    private static final int HEIGHT = 6;
    private static final int GAP = 4;

    private ResourceHudOverlay() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        List<ResourceSyncS2CPayload.Bar> bars = ResourceClientStore.get();
        if (bars.isEmpty()) return;

        int y = TOP;
        for (ResourceSyncS2CPayload.Bar bar : bars) {
            graphics.fill(X - 1, y - 1, X + WIDTH + 1, y + HEIGHT + 1, 0xA0000000);
            graphics.fill(X, y, X + WIDTH, y + HEIGHT, 0xFF202020);
            int fill = bar.max() <= 0 ? 0 : Math.round(WIDTH * Math.min(1f, bar.value() / (float) bar.max()));
            if (fill > 0) graphics.fill(X, y, X + fill, y + HEIGHT, 0xFF000000 | bar.color());
            graphics.drawString(mc.font, bar.value() + "/" + bar.max(), X + WIDTH + 4, y - 1, 0xFFFFFFFF, true);
            y += HEIGHT + GAP;
        }
    }
}
