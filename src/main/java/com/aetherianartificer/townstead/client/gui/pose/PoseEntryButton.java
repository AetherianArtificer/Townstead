package com.aetherianartificer.townstead.client.gui.pose;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Button for one entry in the pose picker. Renders the standard vanilla button
 * background and centered label, then overlays Emotecraft's per-emote icon (if
 * any) at the left edge so the user can recognise an emote by its picture, the
 * way Emotecraft's own B-menu does.
 */
class PoseEntryButton extends Button {
    private static final int ICON_SIZE = 22;
    private static final int ICON_PADDING = 3;

    private final ResourceLocation icon;

    PoseEntryButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            OnPress onPress,
            ResourceLocation icon
    ) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (icon == null) return;
        int size = Math.min(getHeight() - 4, ICON_SIZE);
        int ix = getX() + ICON_PADDING;
        int iy = getY() + (getHeight() - size) / 2;
        //? if neoforge {
        graphics.blit(icon, ix, iy, 0, 0, size, size, size, size);
        //?} else {
        /*graphics.blit(icon, ix, iy, 0, 0, size, size, size, size);
        *///?}
    }
}
