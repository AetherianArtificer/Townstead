package com.aetherianartificer.townstead.mixin.compat.cybernetics;

import com.aetherianartificer.townstead.compat.cybernetics.FaceplateAlias;
import com.aetherianartificer.townstead.compat.cybernetics.FaceplateDisguiseClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cybernetics draws the Mojang skin of whoever a player's custom name names, meant for a faceplate
 * alias. MCA keeps every named player's given name there, so a stranger's skin covered the body.
 * The skin follows a worn alias only, and none at all when the alias names a player here, who is
 * drawn instead.
 */
@Pseudo
@Mixin(targets = "com.perigrine3.createcybernetics.client.skin.SkinModifierManager", remap = false)
public abstract class FaceplateSkinAliasMixin {

    @WrapOperation(method = "getPlayerSkinState", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getCustomName()Lnet/minecraft/network/chat/Component;"))
    private static Component townstead$wornAliasOnly(AbstractClientPlayer player, Operation<Component> original) {
        if (original.call(player) == null || FaceplateDisguiseClient.showsPlayer(player)) return null;
        return FaceplateAlias.worn(player);
    }
}
