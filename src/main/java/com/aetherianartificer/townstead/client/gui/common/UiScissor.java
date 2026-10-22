package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Scissor rectangles are given in real GUI coordinates whatever the pose, so a screen drawn
 * through a scaled pose sets its scale here and every clip goes through {@link #enable}.
 */
public final class UiScissor {
    private static float scale = 1f;

    private UiScissor() {}

    /** The factor from the screen's own coordinates to real GUI coordinates; 1 when drawn unscaled. */
    public static void scale(float value) { scale = value; }

    public static void enable(GuiGraphics g, int x, int y, int right, int bottom) {
        if (scale == 1f) {
            g.enableScissor(x, y, right, bottom);
            return;
        }
        // Round inward so a clip never shows more than the panel it belongs to.
        g.enableScissor((int) Math.ceil(x * scale), (int) Math.ceil(y * scale),
                (int) Math.floor(right * scale), (int) Math.floor(bottom * scale));
    }
}
