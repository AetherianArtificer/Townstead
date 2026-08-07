package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityMCA.class)
public abstract class VillagerServerTickMixin {

    // Entity-local duplicate guard: Connector can expose both Forge and Fabric
    // dispatch paths. Unlike the old global id map this cannot leak on unload or
    // collide when two dimensions reuse the same numeric entity id.
    @Unique private long townstead$lastServerTick = Long.MIN_VALUE;

    //? if neoforge {
    @Inject(method = "aiStep", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_8107_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$serverTick(CallbackInfo ci) {
        VillagerEntityMCA villager = (VillagerEntityMCA) (Object) this;
        long gameTime = villager.level().getGameTime();
        if (townstead$lastServerTick == gameTime) return;
        townstead$lastServerTick = gameTime;
        VillagerServerTickDispatcher.tick(villager);
    }
}
