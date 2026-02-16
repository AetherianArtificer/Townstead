package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.client.gui.InteractScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InteractScreen.class)
public interface InteractScreenAccessor {
    @Invoker("hoveringOverText")
    boolean townstead$invokeHoveringOverText(int x, int y, int width);
}
