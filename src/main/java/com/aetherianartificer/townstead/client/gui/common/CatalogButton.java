package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Native focus, activation and narration with Townstead's shared vanilla button renderer. */
public class CatalogButton extends Button {
    private static final int ICON = 9;
    private static final int GAP = 4;
    private final BooleanSupplier selected;
    private net.minecraft.resources.ResourceLocation icon;
    public CatalogButton(int x, int y, int width, int height, Component label,
                         BooleanSupplier selected, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
        this.selected = selected;
    }
    /** A 9px mark beside the label, centred together, like the Quest Ledger's icon buttons. */
    public CatalogButton icon(net.minecraft.resources.ResourceLocation icon) {
        this.icon = icon;
        return this;
    }
    @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        Controls.Rect rect = new Controls.Rect(getX(), getY(), width, height);
        boolean on = selected.getAsBoolean();
        if (icon == null) {
            Controls.drawButton(g, font, rect, getMessage().getString(), on, isHoveredOrFocused(), active);
            return;
        }
        Controls.drawButtonPlate(g, rect, on, isHoveredOrFocused(), active);
        String label = getMessage().getString();
        int left = getX() + Math.max(2, (width - (ICON + GAP + font.width(label))) / 2);
        g.blit(icon, left, getY() + (height - ICON) / 2, 0, 0, ICON, ICON, ICON, ICON);
        g.drawString(font, label, left + ICON + GAP, getY() + (height - font.lineHeight) / 2 + 1,
                Controls.buttonInk(on, isHoveredOrFocused(), active), true);
    }
}
