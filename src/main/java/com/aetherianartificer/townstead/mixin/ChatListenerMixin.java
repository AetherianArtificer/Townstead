package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route villager-prefixed system chat into the dialogue box while it's open. */
@Mixin(ChatListener.class)
public class ChatListenerMixin {

    //? if neoforge {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "handleSystemMessage", remap = false, require = 0, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$routeVillagerLineToDialogue(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (!(screen instanceof RpgDialogueScreen rpg)) return;

        Component line = rpg.tryExtractExternalVillagerLine(message);
        if (line == null) return;

        rpg.setIncomingChatLine(line);
        ci.cancel();
    }
}
