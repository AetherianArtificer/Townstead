package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The shared frame and panel kit every Townstead screen is built from: wooden frames, plank
 * walls, and the two grades of parchment. Uses vanilla block-atlas textures, so it needs no
 * texture files of its own.
 *
 * <p>This lives in {@code gui.common} because it belongs to no one screen. It was born inside
 * the Field Post package and four other screens ended up reaching across into a sibling feature
 * for it, which is the difference between having a design system and copying from whichever
 * screen happened to be written first. Anything drawn by more than one screen belongs here.</p>
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
     * The interior a wooden frame encloses: dark planks under a soft wash, with a lit top lip.
     *
     * <p>{@link #drawWoodenFrame} deliberately leaves the content rect alone, which means a screen
     * that only calls the frame gets a hole with the world showing through it. This is the other
     * half, and every framed screen wants it.</p>
     */
    public static void drawInnerPanel(GuiGraphics g, int x, int y, int w, int h) {
        tileTexture(g, PLANK_DARK, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0xC8241A0E);
        g.fill(x, y, x + w, y + 1, Palette.DESK_LIP);
    }

    /**
     * A pane sunk into the panel: the columns and strips that hold rows. Darker than the panel it
     * sits in, so a list reads as a recess rather than as another rectangle lying on top.
     */
    public static void drawWell(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Palette.WELL);
        Palette.drawOutline(g, x, y, x + w, y + h, Palette.WELL_EDGE);
    }

    /**
     * The interior of a framed panel, darkened to whatever the player has set their chat background
     * to and no further.
     *
     * <p>This is what the Field Post fills its panels with, and it is why that screen sits on the
     * world rather than covering it. It also means a player who needs more contrast has already
     * told us so, in a setting they understand, and gets it everywhere at once.</p>
     */
    public static void drawChatPanel(GuiGraphics g, int x, int y, int w, int h) {
        double opacity = net.minecraft.client.Minecraft.getInstance()
                .options.textBackgroundOpacity().get();
        int alpha = (int) (opacity * 255.0) & 0xFF;
        g.fill(x, y, x + w, y + h, alpha << 24);
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

    // Vanilla empty-map texture: 64×64 with a 7px wood-look border and true parchment
    // interior. The Calendar established this as the family's real-parchment panel.
    //? if >=1.21 {
    private static final net.minecraft.resources.ResourceLocation MAP_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/map/map_background.png");
    //?} else {
    /*private static final net.minecraft.resources.ResourceLocation MAP_TEXTURE =
            new net.minecraft.resources.ResourceLocation("minecraft", "textures/map/map_background.png");
    *///?}
    private static final int MAP_TEX_SIZE = 64;
    private static final int MAP_FRAME = 7;

    /**
     * 9-slices the vanilla empty-map texture across a rect: crisp 1:1 corners, stretched
     * edges, stretched parchment interior. This is what "parchment" looks like in the
     * Townstead family (see the Calendar); the tiled log-top panel is a rougher paper.
     */
    public static void drawMapParchment(GuiGraphics g, int x, int y, int w, int h) {
        final int f = MAP_FRAME;
        final int t = MAP_TEX_SIZE;
        final int srcInner = t - 2 * f;
        final int dstInnerW = w - 2 * f;
        final int dstInnerH = h - 2 * f;

        blitMapSlice(g, x,         y,         f, f,         0,     0,     f,        f);
        blitMapSlice(g, x + w - f, y,         f, f,         t - f, 0,     f,        f);
        blitMapSlice(g, x,         y + h - f, f, f,         0,     t - f, f,        f);
        blitMapSlice(g, x + w - f, y + h - f, f, f,         t - f, t - f, f,        f);

        blitMapSlice(g, x + f,     y,         dstInnerW, f, f,     0,     srcInner, f);
        blitMapSlice(g, x + f,     y + h - f, dstInnerW, f, f,     t - f, srcInner, f);
        blitMapSlice(g, x,         y + f,     f, dstInnerH, 0,     f,     f,        srcInner);
        blitMapSlice(g, x + w - f, y + f,     f, dstInnerH, t - f, f,     f,        srcInner);

        blitMapSlice(g, x + f, y + f, dstInnerW, dstInnerH, f, f, srcInner, srcInner);
    }

    /**
     * A clean rectangular sheet cut from the map's parchment interior. Use this for deeds and
     * forms that sit inside an existing frame; unlike {@link #drawMapParchment}, it contributes
     * no second ragged frame, and unlike {@link #drawParchmentPanel}, it has no wood grain.
     */
    public static void drawPaperSheet(GuiGraphics g, int x, int y, int w, int h) {
        blitMapSlice(g, x, y, w, h, MAP_FRAME, MAP_FRAME,
                MAP_TEX_SIZE - MAP_FRAME * 2, MAP_TEX_SIZE - MAP_FRAME * 2);
        Palette.drawOutline(g, x, y, x + w, y + h, Palette.BRASS_DEEP);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x66FFF0C8);
    }

    private static void blitMapSlice(GuiGraphics g, int x, int y, int dw, int dh,
                                     int u, int v, int sw, int sh) {
        g.blit(MAP_TEXTURE, x, y, dw, dh, (float) u, (float) v, sw, sh, MAP_TEX_SIZE, MAP_TEX_SIZE);
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
        // 1.20.1 only: flush the atlas batch BEFORE disabling scissor. GuiGraphics queues
        // atlas-sprite draws and flushes them later in a combined render pass; on 1.20.1 that
        // flush happens after scissor is disabled, so tiles render unclipped and bleed outside
        // the intended strip (showing as a "dirt-looking" plank texture over tabs/search).
        // 1.21.1's GuiGraphics handles this correctly on its own.
        //? if <1.21 {
        /*g.flush();
        *///?}
        g.disableScissor();
    }
}
