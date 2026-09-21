package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class CatalogBadgeRenderer {
    private static final net.minecraft.resources.ResourceLocation HANGOUT =
            net.minecraft.resources.ResourceLocation.tryParse("townstead:textures/gui/catalog/hangout.png");
    private CatalogBadgeRenderer() {}
    public static int badge(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int width = font.width(text) + 8;
        g.fill(x, y, x + width, y + 13, 0xFF122820);
        g.fill(x, y, x + 1, y + 13, color);
        g.drawString(font, text, x + 4, y + 2, color, false);
        return width;
    }
    /** Blockbench-authored bench and conversation bubble, distinct from recognition and pin marks. */
    public static void hangout(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 14, y + 12, 0xFF172C24);
        g.blit(HANGOUT, x - 1, y - 2, 0, 0, 16, 16, 16, 16);
    }
}
