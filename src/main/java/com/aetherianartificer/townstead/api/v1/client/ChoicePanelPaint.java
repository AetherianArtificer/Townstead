package com.aetherianartificer.townstead.api.v1.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** What Townstead just painted: the panel bounds, its fade alpha, and the rows in screen coordinates. */
public record ChoicePanelPaint(
        Screen screen,
        GuiGraphics graphics,
        Font font,
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        float alpha,
        List<ChoiceRow> rows
) {
    public ChoicePanelPaint {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
