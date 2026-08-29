package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button made of the same material as the screens it sits on.
 *
 * <p>Vanilla's grey widget texture is the one thing on a parchment screen that does not belong to
 * it, and on the Career record it was the only unstyled element: correct, and shouting. This draws
 * a pressed-paper plate in the family palette instead.</p>
 */
public class ParchmentButton extends Button {

    private static final int FACE = 0xFFC9A96A;
    private static final int FACE_HOVER = 0xFFE0C089;
    private static final int FACE_OFF = 0xFFB5AC96;
    private static final int EDGE_HI = 0xFFE4CE9E;
    private static final int EDGE_LO = 0xFF6E5228;
    private static final int EDGE_OFF_HI = 0xFFC8C0AC;
    private static final int EDGE_OFF_LO = 0xFF8A8270;
    private static final int INK = 0xFF2A1C0C;
    private static final int INK_OFF = 0xFF7A7263;

    public ParchmentButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean on = active;
        boolean hot = on && isHovered();

        g.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x30140F08);
        g.fill(x, y, x + w, y + h, on ? (hot ? FACE_HOVER : FACE) : FACE_OFF);
        // Light from the top left, the same direction the frames and plaques are lit from.
        g.fill(x, y, x + w, y + 1, on ? EDGE_HI : EDGE_OFF_HI);
        g.fill(x, y, x + 1, y + h, on ? EDGE_HI : EDGE_OFF_HI);
        g.fill(x, y + h - 1, x + w, y + h, on ? EDGE_LO : EDGE_OFF_LO);
        g.fill(x + w - 1, y, x + w, y + h, on ? EDGE_LO : EDGE_OFF_LO);

        var font = Minecraft.getInstance().font;
        Component message = getMessage();
        int textWidth = font.width(message);
        // Clip rather than overflow: a long rank name used to run out through the button's edge.
        int room = w - 8;
        if (textWidth > room) {
            g.enableScissor(x + 4, y, x + w - 4, y + h);
        }
        g.drawString(font, message, x + (w - Math.min(textWidth, room)) / 2,
                y + (h - font.lineHeight) / 2 + 1, on ? INK : INK_OFF, false);
        if (textWidth > room) {
            g.disableScissor();
        }
    }
}
