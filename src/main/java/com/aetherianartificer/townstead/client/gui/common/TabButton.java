package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Keyboard-accessible button using the shared Field Post / Order Sheet tab treatment. */
public final class TabButton extends Button {
    private final BooleanSupplier selected;

    public TabButton(Controls.Rect bounds, Component label, BooleanSupplier selected, OnPress action) {
        super(bounds.x(), bounds.y(), bounds.w(), bounds.h(), label, action, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        Controls.drawTabs(g, Minecraft.getInstance().font,
                new Controls.Rect[]{new Controls.Rect(getX(), getY(), width, height)},
                new String[]{getMessage().getString()}, selected.getAsBoolean() ? 0 : -1,
                isHoveredOrFocused() ? 0 : -1, false);
    }
}
