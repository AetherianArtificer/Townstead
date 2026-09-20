package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.compat.hats.HatsCompat;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.InteractScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies on every layout rebuild, including Back and constraint updates, not just initial opening. */
@Mixin(AbstractDynamicScreen.class)
public abstract class VillagerDressMenuMixin extends Screen {
    protected VillagerDressMenuMixin(Component title) { super(title); }

    @Inject(method = "setLayout", remap = false, at = @At("TAIL"))
    private void townstead$configureDressButtons(String layout, CallbackInfo ci) {
        if (!((Object) this instanceof InteractScreen)) return;
        for (var child : children()) {
            if (!(child instanceof Button button)
                    || !(button.getMessage().getContents() instanceof TranslatableContents text)) continue;
            String key = text.getKey();
            if (!HatsCompat.present() && (key.equals("gui.button.townstead_dress") || key.equals("gui.button.give_hat"))) {
                button.visible = false;
                button.active = false;
            } else if (key.equals("gui.button.armor")) {
                button.setMessage(Component.translatable("gui.townstead.button.auto_armor"));
                button.setTooltip(Tooltip.create(Component.translatable("gui.townstead.button.auto_armor.tooltip")));
            }
        }
    }
}
