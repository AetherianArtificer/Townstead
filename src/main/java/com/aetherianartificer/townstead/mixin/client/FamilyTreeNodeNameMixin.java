package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.naming.NameClientStore;
import com.mojang.blaze3d.systems.RenderSystem;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows a player's family name on family tree screens. The stored node keeps the given name, because
 * MCA Descendants copies it into the player's custom name, which the editor's name field then reads.
 * In singleplayer the client can hold the server's own nodes, so only the render thread is changed.
 */
@Mixin(value = FamilyTreeNode.class, remap = false)
public abstract class FamilyTreeNodeNameMixin {

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void townstead$showFamilyName(CallbackInfoReturnable<String> cir) {
        if (!RenderSystem.isOnRenderThread()) return;
        FamilyTreeNode node = (FamilyTreeNode) (Object) this;
        if (node.isPlayer()) cir.setReturnValue(NameClientStore.playerFullName(node.id(), cir.getReturnValue()));
    }
}
