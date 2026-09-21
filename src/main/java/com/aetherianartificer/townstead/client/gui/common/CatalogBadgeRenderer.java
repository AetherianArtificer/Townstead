package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class CatalogBadgeRenderer {
    private static final net.minecraft.resources.ResourceLocation HANGOUT =
            net.minecraft.resources.ResourceLocation.tryParse("townstead:textures/gui/catalog/hangout.png");
    private CatalogBadgeRenderer() {}
    public static void hangoutLabel(GuiGraphics g, Font font, String text, int x, int y, int color) {
        hangout(g, x, y - 2);
        g.drawString(font, text, x + 17, y, color, false);
    }
    /** Blockbench-authored bench and conversation bubble, distinct from recognition and pin marks. */
    public static void hangout(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 14, y + 12, 0xFF172C24);
        g.blit(HANGOUT, x - 1, y - 2, 0, 0, 16, 16, 16, 16);
    }
}
