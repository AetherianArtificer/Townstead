package com.aetherianartificer.townstead.client.gui.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A vanilla button with a ledger icon beside its label, centred together. */
public final class QuestIconButton extends Button {
    private static final int GAP = 4;
    private final QuestIcons.Icon icon;

    public QuestIconButton(int x, int y, int width, int height, Component message, OnPress onPress, QuestIcons.Icon icon) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int color) {
        int textWidth = font.width(getMessage());
        int total = icon.size() + GAP + textWidth;
        int left = getX() + Math.max(2, (getWidth() - total) / 2);
        QuestIcons.draw(g, icon, left, getY() + (getHeight() - icon.size()) / 2);
        g.drawString(font, getMessage(), left + icon.size() + GAP, getY() + (getHeight() - 8) / 2, color);
    }
}
