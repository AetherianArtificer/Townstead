package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps MCA villagers from starting sleep in a bed reserved by an Ownership Deed. */
@Mixin(SleepInBed.class)
public abstract class SleepInBedOwnershipMixin {

    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void townstead$respectPrivateRoom(ServerLevel level, LivingEntity entity,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null || !home.dimension().equals(level.dimension())) return;
        if (!com.aetherianartificer.townstead.storage.RoomOwnershipAccess
                .maySleep(level, villager, home.pos())) {
            cir.setReturnValue(false);
        }
    }
}
