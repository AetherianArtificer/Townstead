package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.gui.quest.QuestGiverGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outlines a tracked quest's giver, decided each frame so nothing is left on the entity. */
@Mixin(Minecraft.class)
public abstract class QuestGiverGlowMixin {
    //? if neoforge {
    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_91314_(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$glowQuestGiver(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && QuestGiverGlow.glows(entity)) cir.setReturnValue(true);
    }
}
