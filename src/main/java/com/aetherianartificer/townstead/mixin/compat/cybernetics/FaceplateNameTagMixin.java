package com.aetherianartificer.townstead.mixin.compat.cybernetics;

import com.aetherianartificer.townstead.compat.cybernetics.FaceplateAlias;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Cybernetics puts a player's bare custom name on their nameplate; only a worn alias replaces the full name. */
@Pseudo
@Mixin(targets = "com.perigrine3.createcybernetics.client.FaceplateClientHandler", remap = false)
public abstract class FaceplateNameTagMixin {

    @WrapOperation(method = "onRenderNameTag", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getCustomName()Lnet/minecraft/network/chat/Component;"))
    private static Component townstead$wornAliasOnly(Player player, Operation<Component> original) {
        return original.call(player) == null ? null : FaceplateAlias.worn(player);
    }
}
