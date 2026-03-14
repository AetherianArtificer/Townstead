package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractDynamicScreen.class)
public interface AbstractDynamicScreenAccessor {
    @Invoker(value = "hoveringOverIcon", remap = false)
    boolean townstead$invokeHoveringOverIcon(String icon);

    @Invoker(value = "drawHoveringIconText", remap = false)
    void townstead$invokeDrawHoveringIconText(GuiGraphics context, Component text, String icon);
}
