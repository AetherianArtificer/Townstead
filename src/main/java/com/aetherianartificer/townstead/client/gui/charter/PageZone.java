package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Something on the page that can be clicked: a row, a field, the emblem. The page draws it; this
 * only gives it a place in the focus order, a narration, and a hover state the page reads.
 */
final class PageZone extends Button {
    PageZone(Controls.Rect rect, Component narration, Runnable action) {
        super(rect.x(), rect.y(), rect.w(), rect.h(), narration, b -> action.run(), DEFAULT_NARRATION);
    }

    boolean hot() { return isHoveredOrFocused(); }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}
