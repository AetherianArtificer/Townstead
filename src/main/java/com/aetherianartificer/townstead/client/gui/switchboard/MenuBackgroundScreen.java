package com.aetherianartificer.townstead.client.gui.switchboard;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A screen drawn over the world-creation background rather than the blurred world behind it. */
abstract class MenuBackgroundScreen extends Screen {

    protected MenuBackgroundScreen(Component title) {
        super(title);
    }

    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanorama(g, partialTick);
        renderBlurredBackground(partialTick);
        renderMenuBackground(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        renderDirtBackground(g);
    }
    *///?}
}
