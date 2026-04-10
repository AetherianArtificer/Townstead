package com.aetherianartificer.townstead.client.gui.fieldpost;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders wooden/parchment frames and panels for the Field Post UI.
 * Uses vanilla block atlas textures (no custom texture files needed).
 */
public final class FrameRenderer {
    private FrameRenderer() {}

    public static final String PLANK_DARK = "minecraft:block/dark_oak_planks";
    public static final String PLANK_LIGHT = "minecraft:block/birch_planks";
    public static final String PARCHMENT_TEX = "minecraft:block/stripped_birch_log_top";

    public static final int FRAME_SHADOW = 0xFF1A0F05;
    public static final int FRAME_HIGHLIGHT = 0x40FFDEA0;

    /**
     * Draws a wooden frame *around* the given content rect (frame is outside, content unchanged).
     */
    public static void drawWoodenFrame(GuiGraphics g, int x, int y, int w, int h, int thickness) {
        int outX = x - thickness;
        int outY = y - thickness;
        int outW = w + thickness * 2;
        int outH = h + thickness * 2;

        // Outer shadow line
        g.fill(outX - 1, outY - 1, outX + outW + 1, outY, FRAME_SHADOW);
        g.fill(outX - 1, outY + outH, outX + outW + 1, outY + outH + 1, FRAME_SHADOW);
        g.fill(outX - 1, outY, outX, outY + outH, FRAME_SHADOW);
        g.fill(outX + outW, outY, outX + outW + 1, outY + outH, FRAME_SHADOW);

        // Plank frame - four strips
        tileTexture(g, PLANK_DARK, outX, outY, outW, thickness);                // top
        tileTexture(g, PLANK_DARK, outX, y + h, outW, thickness);               // bottom
        tileTexture(g, PLANK_DARK, outX, y, thickness, h);                      // left
        tileTexture(g, PLANK_DARK, x + w, y, thickness, h);                     // right

        // Highlight along top/left of frame
        g.fill(outX, outY, outX + outW, outY + 1, FRAME_HIGHLIGHT);
        g.fill(outX, outY, outX + 1, outY + outH, FRAME_HIGHLIGHT);

        // Inner shadow against content (inside bevel)
        g.fill(x - 1, y - 1, x + w + 1, y, FRAME_SHADOW);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, FRAME_SHADOW);
        g.fill(x - 1, y, x, y + h, FRAME_SHADOW);
        g.fill(x + w, y, x + w + 1, y + h, FRAME_SHADOW);
    }

    /**
     * Fills an area with a parchment-tinted background (warm off-white).
     */
    public static void drawParchmentPanel(GuiGraphics g, int x, int y, int w, int h) {
        tileTexture(g, PARCHMENT_TEX, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0x55000000);
        g.fill(x, y, x + w, y + h, 0x35E8D0A0); // warm parchment tint
    }

    /**
     * Dark wood panel background (for the viewport interior).
     */
    public static void drawWoodPanel(GuiGraphics g, int x, int y, int w, int h) {
        tileTexture(g, PLANK_DARK, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0xA0000000);
    }

    /**
     * Tiles a texture across an area. Uses nested scissor for clipping partial tiles.
     */
    public static void tileTexture(GuiGraphics g, String texture, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        final int tileSize = 16;
        g.enableScissor(x, y, x + w, y + h);
        for (int ty = 0; ty < h; ty += tileSize) {
            for (int tx = 0; tx < w; tx += tileSize) {
                CellTextures.blit(g, texture, x + tx, y + ty, tileSize);
            }
        }
        g.disableScissor();
    }
}
