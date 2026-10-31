package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.politics.charter.VillageRenames;
import net.conczin.mca.network.c2s.RenameVillageMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A village held by a faction is renamed from the blueprint only by those who may amend its charter. */
@Mixin(RenameVillageMessage.class)
public abstract class RenameVillageMessageMixin {
    //? if <1.21 {
    /*@Shadow(remap = false) @Final
    private int id;
    *///?}

    //? if >=1.21 {
    @Inject(method = "handleServer", at = @At("HEAD"), cancellable = true, remap = false)
    //?} else {
    /*@Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$leadersOnly(ServerPlayer player, CallbackInfo ci) {
        //? if >=1.21 {
        int village = ((RenameVillageMessage) (Object) this).id();
        //?} else {
        /*int village = this.id;
        *///?}
        if (!VillageRenames.allowed(player, village)) ci.cancel();
    }
}
