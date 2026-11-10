package com.aetherianartificer.townstead.client.gui.naming;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A quiet line showing a UUID with a small copy mark. The relation fields accept what it copies. */
public final class UuidLine extends AbstractButton {

    public static final int HEIGHT = 10;
    private static final int COPIED_MS = 1500;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int HOT = 0xFFE0E0E0;

    private final Font font;
    private final String uuid;
    private long copiedUntil;

    public UuidLine(Font font, int x, int y, int width, String uuid) {
        super(x, y, width, HEIGHT, Component.literal(uuid));
        this.font = font;
        this.uuid = uuid;
        setTooltip(Tooltip.create(Component.literal(uuid).append("\n")
                .append(Component.translatable("gui.townstead.editor.copy_uuid"))));
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().keyboardHandler.setClipboard(uuid);
        copiedUntil = System.currentTimeMillis() + COPIED_MS;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int color = isHoveredOrFocused() ? HOT : MUTED;
        int textWidth = getWidth() - 12;
        String shown = System.currentTimeMillis() < copiedUntil
                ? Component.translatable("gui.townstead.editor.copied").getString()
                : font.width(uuid) <= textWidth ? uuid
                : font.plainSubstrByWidth(uuid, textWidth - font.width("...")) + "...";
        g.drawString(font, shown, getX(), getY() + 1, color);

        // Two overlapping sheets, the usual copy mark.
        int right = getX() + getWidth();
        int top = getY() + 1;
        outline(g, right - 8, top, right - 2, top + 6, color);
        outline(g, right - 6, top + 2, right, top + 8, color);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
