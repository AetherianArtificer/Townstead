package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Native focus, activation and narration with Townstead's shared vanilla button renderer. */
public class CatalogButton extends Button {
    private final BooleanSupplier selected;
    public CatalogButton(int x, int y, int width, int height, Component label,
                         BooleanSupplier selected, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
        this.selected = selected;
    }
    @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        Controls.drawButton(g, Minecraft.getInstance().font,
                new Controls.Rect(getX(), getY(), width, height), getMessage().getString(),
                selected.getAsBoolean(), isHoveredOrFocused(), active);
    }
}
