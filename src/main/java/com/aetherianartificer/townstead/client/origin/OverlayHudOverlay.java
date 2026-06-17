package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Blits each active {@code overlay} gene's texture full-screen, stretched to the window
 * with the gene's alpha. Active ids come from {@link OverlayClientStore} (server-synced);
 * the texture and alpha come from the gene catalog. Drawn under the resource bars so
 * vignettes sit behind the meters. Shared by both versions' HUD registration.
 */
public final class OverlayHudOverlay {

    private OverlayHudOverlay() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        List<String> active = OverlayClientStore.get();
        if (active.isEmpty()) return;

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        for (String geneId : active) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(geneId);
            if (gene == null || !gene.isOverlay()) continue;
            ResourceLocation texture = DataPackLang.parseId(gene.overlayTexture());
            if (texture == null) continue;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, gene.overlayAlpha());
            // u=0,v=0 with uWidth==texWidth samples the whole image, so it stretches to fit
            // regardless of the texture's real pixel size.
            graphics.blit(texture, 0, 0, w, h, 0f, 0f, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
    }
}
