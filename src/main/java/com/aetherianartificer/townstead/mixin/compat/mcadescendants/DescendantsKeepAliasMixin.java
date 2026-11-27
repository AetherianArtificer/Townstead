package com.aetherianartificer.townstead.mixin.compat.mcadescendants;

import com.aetherianartificer.townstead.compat.cybernetics.FaceplateAlias;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * MCA Descendants sets a player's custom name back to their family tree name every tick. A worn
 * faceplate alias is a name the player chose to go by, so it stays until the mask comes off.
 */
@Pseudo
@Mixin(targets = "net.dannyfather.mca_descendants.events.MCADescendantsEvents", remap = false)
public abstract class DescendantsKeepAliasMixin {

    @WrapOperation(method = "TickEvent", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setCustomName(Lnet/minecraft/network/chat/Component;)V"))
    private static void townstead$keepWornAlias(ServerPlayer player, Component name, Operation<Void> original) {
        if (FaceplateAlias.worn(player) == null) original.call(player, name);
    }
}
