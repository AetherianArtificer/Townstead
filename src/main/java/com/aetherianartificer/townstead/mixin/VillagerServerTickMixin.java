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

    // Fatigue-forced sleepers must be held in REST before MCA ticks its brain;
    // the main dispatcher intentionally remains at TAIL for all other systems.
    //? if neoforge {
    @Inject(method = "aiStep", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_8107_", remap = false, at = @At("HEAD"))
    *///?}
    private void townstead$beforeServerAiStep(CallbackInfo ci) {
        VillagerEntityMCA villager = (VillagerEntityMCA) (Object) this;
        if (!villager.level().isClientSide) {
            if (villager.isSleeping() && villager.level() instanceof net.minecraft.server.level.ServerLevel level) {
                villager.getSleepingPos().ifPresent(bed -> {
                    if (!com.aetherianartificer.townstead.storage.RoomOwnershipAccess
                            .maySleep(level, villager, bed)) {
                        villager.stopSleeping();
                    }
                });
            }
            com.aetherianartificer.townstead.tick.FatigueVillagerTicker.preAiStep(villager);
        }
    }

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
