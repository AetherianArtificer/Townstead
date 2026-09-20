package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Accessible widget backed by the same control artwork as Townstead's other tools. */
final class CharterButton extends Button {
    private boolean selected;

    CharterButton(int x, int y, int width, int height, Component label, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
    }

    void setSelected(boolean selected) { this.selected = selected; }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        Controls.drawButton(g, Minecraft.getInstance().font,
                new Controls.Rect(getX(), getY(), getWidth(), getHeight()), getMessage().getString(),
                selected, isHovered() || isFocused(), active);
    }
}
