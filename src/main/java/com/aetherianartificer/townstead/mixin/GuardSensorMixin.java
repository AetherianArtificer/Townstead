package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses guard enemy detection when the villager's shift schedule says REST.
 * This ensures guards/archers respect their assigned rest hours.
 */
@Mixin(GuardEnemiesSensor.class)
public class GuardSensorMixin {

    //? if neoforge {
    @Inject(method = "doTick", remap = false, at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_5578_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$suppressDuringRest(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        // Check if the villager's current schedule activity is REST
        Activity current = villager.getBrain().getSchedule()
                .getActivityAt((int) (level.getDayTime() % 24000L));
        if (current == Activity.REST) {
            // Clear any existing guard enemy memory so they stop fighting
            //? if neoforge {
            villager.getBrain().eraseMemory(net.conczin.mca.entity.ai.MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            //?} else {
            /*villager.getBrain().eraseMemory(
                    ((net.minecraft.world.entity.ai.memory.MemoryModuleType<?>)
                    forge.net.mca.entity.ai.MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get()));
            *///?}
            ci.cancel();
        }
    }
}
