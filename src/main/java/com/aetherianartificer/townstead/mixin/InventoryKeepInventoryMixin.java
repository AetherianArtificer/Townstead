package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.KeepInventoryGeneType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the death inventory drop for a {@code keep_inventory} race (so nothing is
 * dropped and the inventory stays intact); the respawned player gets it back via the
 * {@code PlayerEvent.Clone} copy. Parity: same hook both branches (SRG name/field on Forge).
 */
@Mixin(net.minecraft.world.entity.player.Inventory.class)
public abstract class InventoryKeepInventoryMixin {

    //? if neoforge {
    @Shadow @Final public net.minecraft.world.entity.player.Player player;
    //?} else {
    /*@Shadow(remap = false) @Final public net.minecraft.world.entity.player.Player f_35978_;
    *///?}

    //? if neoforge {
    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_36071_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$keepInventory(CallbackInfo ci) {
        //? if neoforge {
        net.minecraft.world.entity.player.Player owner = this.player;
        //?} else {
        /*net.minecraft.world.entity.player.Player owner = this.f_35978_;
        *///?}
        if (owner == null || owner.level().isClientSide) return;
        if (!ExpressedGenes.instancesOf(owner, KeepInventoryGeneType.Instance.class).isEmpty()) {
            ci.cancel();
        }
    }
}
