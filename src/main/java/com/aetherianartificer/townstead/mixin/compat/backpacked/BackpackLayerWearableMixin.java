package com.aetherianartificer.townstead.mixin.compat.backpacked;

import com.aetherianartificer.townstead.client.species.RigWearables;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Soft compat with MrCrayfish's Backpacked: nudges the worn backpack onto a non-humanoid rig's back
 * by re-posing the host body bone to the rig's {@code back} base+{@code backpack} delta just before
 * the backpack layer reads it. {@code @Pseudo} + {@code require = 0} so this is inert when Backpacked
 * is absent. The targeted method is the mod's own {@code render(..., Player, ...)} overload (not an MC
 * override), so its name is stable across versions and needs no remap.
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.backpacked.client.renderer.entity.layers.BackpackLayer")
public abstract class BackpackLayerWearableMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/player/Player;FFFFFF)V", at = @At("HEAD"), remap = false, require = 0)
    private void townstead$anchorBackpack(PoseStack pose, MultiBufferSource buffers, int light, Player player,
                                          float limbSwing, float limbSwingAmount, float partialTick,
                                          float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        RigWearables.applyItem(player, "backpack");
    }
}
