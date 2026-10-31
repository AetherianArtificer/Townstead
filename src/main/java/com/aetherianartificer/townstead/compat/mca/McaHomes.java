package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.Optional;

/**
 * Gives a villager a home bed, or takes it away, the way MCA's own set-home does: the bed's POI
 * ticket, HOME, FORCED_HOME, then MCA's village index.
 */
public final class McaHomes {
    private McaHomes() {}

    /** Claims {@code bed} as the villager's home. False when someone else holds it. */
    public static boolean assign(ServerLevel level, VillagerEntityMCA villager, BlockPos bed) {
        Optional<GlobalPos> previous = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (previous.filter(home -> home.dimension().equals(level.dimension()) && home.pos().equals(bed)).isPresent()) {
            return true;
        }
        Optional<BlockPos> claimed = level.getPoiManager().take(
                type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 1);
        if (claimed.isEmpty()) return false;
        villager.releasePoi(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed.immutable()));
        villager.getBrain().setMemory(forcedHome(), true);
        //? if >=1.21 {
        villager.getResidency().onHomeClaimed();
        //?} else {
        /*villager.getResidency().seekHome();
        *///?}
        return true;
    }

    /** Lets go of the villager's home and its bed, so MCA looks for another. */
    public static void release(VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) return;
        villager.releasePoi(MemoryModuleType.HOME);
        //? if >=1.21 {
        villager.getResidency().invalidateHome(home.get());
        //?} else {
        /*villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(forcedHome());
        if (home.get().dimension().equals(villager.level().dimension())
                && villager.getSleepingPos().filter(home.get().pos()::equals).isPresent()) {
            villager.stopSleeping();
        }
        *///?}
    }

    private static MemoryModuleType<Boolean> forcedHome() {
        //? if >=1.21 {
        return MemoryModuleTypeMCA.FORCED_HOME;
        //?} else {
        /*return MemoryModuleTypeMCA.FORCED_HOME.get();
        *///?}
    }
}
