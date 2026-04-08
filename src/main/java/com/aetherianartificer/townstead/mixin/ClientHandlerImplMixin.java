package com.aetherianartificer.townstead.mixin;

//? if neoforge {
import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.network.s2c.InteractionDialogueResponse;
import net.conczin.mca.network.s2c.VillagerMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts MCA's dialogue packet handlers to route to {@link RpgDialogueScreen}
 * when it is the active screen, instead of MCA's default InteractScreen.
 */
@Mixin(ClientHandlerImpl.class)
public class ClientHandlerImplMixin {

    @Inject(method = "handleDialogueResponse", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectDialogueResponse(InteractionDialogueResponse message, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RpgDialogueScreen rpg) {
            rpg.setDialogue(message.question(), message.answers());
            ci.cancel();
        }
    }

    @Inject(method = "handleDialogueQuestionResponse", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectDialogueQuestionResponse(InteractionDialogueQuestionResponse message, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RpgDialogueScreen rpg) {
            rpg.setLastPhrase(message.questionText(), message.silent());
            ci.cancel();
        }
    }

    @Inject(method = "handleVillagerMessage", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectVillagerMessage(VillagerMessage message, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RpgDialogueScreen rpg && rpg.isVillager(message.uuid())) {
            rpg.setFinalPhrase(message.message());
            ci.cancel();
            return;
        }

        // Strip effect tags from chat-bound messages so <yell>Help!</yell> shows as "Help!"
        String raw = message.message().getString();
        if (raw.contains("<") && raw.contains(">")) {
            String stripped = EffectTagParser.stripTags(raw);
            if (!stripped.equals(raw)) {
                Component cleanMessage = Component.literal(stripped);
                MutableComponent full = message.prefix().copy().append(cleanMessage);
                Minecraft.getInstance().getChatListener().handleSystemMessage(full, false);
                SpeechManager.INSTANCE.onChatMessage(cleanMessage, message.uuid());
                ci.cancel();
            }
        }
    }
}
//?} else {
/*
import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import net.conczin.mca.network.ClientInteractionManagerImpl;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.network.s2c.InteractionDialogueResponse;
import net.conczin.mca.network.s2c.VillagerMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientInteractionManagerImpl.class)
public class ClientHandlerImplMixin {

    @Inject(method = "handleDialogueResponse", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectDialogueResponse(InteractionDialogueResponse message, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RpgDialogueScreen rpg) {
            rpg.setDialogue(message.question, message.answers);
            ci.cancel();
        }
    }

    @Inject(method = "handleDialogueQuestionResponse", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectDialogueQuestionResponse(InteractionDialogueQuestionResponse message, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RpgDialogueScreen rpg) {
            rpg.setLastPhrase(message.getQuestionText(), message.silent);
            ci.cancel();
        }
    }

    @Inject(method = "handleVillagerMessage", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectVillagerMessage(VillagerMessage message, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RpgDialogueScreen rpg && rpg.isVillager(message.getUuid())) {
            rpg.setFinalPhrase(message.getContent());
            ci.cancel();
            return;
        }

        String raw = message.getContent().getString();
        if (raw.contains("<") && raw.contains(">")) {
            String stripped = EffectTagParser.stripTags(raw);
            if (!stripped.equals(raw)) {
                // Rebuild the full "Name: message" with stripped content
                String fullRaw = message.getMessage().getString();
                String fullStripped = EffectTagParser.stripTags(fullRaw);
                Minecraft.getInstance().player.displayClientMessage(Component.literal(fullStripped), false);
                ci.cancel();
            }
        }
    }
}
*///?}
