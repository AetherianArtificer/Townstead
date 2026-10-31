package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.mca.McaRegistryCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Keeps MCA guards and archers from chasing past their village: a foe more than {@link #MARGIN}
 * blocks beyond the border is let go. A guard following a player, or one being attacked by that
 * foe, still fights. Stopgap until guards answer through martial jobs and the alert ladder.
 */
public final class GuardLeash {
    private static final int MARGIN = 16;
    private static final int INTERVAL = 10;

    private GuardLeash() {}

    /** Whether {@code guard} should leave {@code target} alone for being too far from home. */
    public static boolean beyond(LivingEntity guard, LivingEntity target) {
        if (!(guard instanceof VillagerEntityMCA villager) || villager.level().isClientSide) return false;
        if (!McaRegistryCompat.isGuardOrArcher(villager.getVillagerData().getProfession())) return false;
        if (target instanceof Mob mob && mob.getTarget() == guard) return false;
        //? if >=1.21 {
        if (villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING)) return false;
        //?} else {
        /*if (villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get())) return false;
        *///?}
        return villager.getResidency().getHomeVillage()
                .map(village -> !village.isWithinBorder(target.blockPosition(), MARGIN))
                .orElse(false);
    }

    /** Drops a target the guard has already chased past the leash. */
    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % INTERVAL != 0) return;
        LivingEntity target = villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null || !beyond(villager, target)) return;
        villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
