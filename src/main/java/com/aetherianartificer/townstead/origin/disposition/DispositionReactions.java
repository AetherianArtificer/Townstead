package com.aetherianartificer.townstead.origin.disposition;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

/**
 * The hostile half of the disposition seam, for villagers: when something it regards as hostile is
 * nearby, an armed villager (a guard) engages it, an unarmed one flees. The friendly half (wild
 * undead ignoring kin) lives in the change-target filter; wild hostile mobs already target via their
 * own AI, so this only adds the folk reaction the disposition implies. Throttled, called per villager.
 */
public final class DispositionReactions {

    private static final int INTERVAL = 20;
    private static final double RADIUS = 12.0;

    private DispositionReactions() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        if ((villager.level().getGameTime() + villager.getId()) % INTERVAL != 0) return;
        LivingEntity threat = nearestHostile(villager);
        if (threat == null) return;
        if (isArmed(villager)) {
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threat);   // a guard engages
        } else {
            flee(villager, threat);                                                  // everyone else runs
        }
    }

    private static LivingEntity nearestHostile(VillagerEntityMCA villager) {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity other : villager.level().getEntitiesOfClass(
                LivingEntity.class, villager.getBoundingBox().inflate(RADIUS))) {
            if (other == villager) continue;
            if (Dispositions.between(villager, other) != Disposition.HOSTILE) continue;
            double sq = villager.distanceToSqr(other);
            if (sq < bestSq) { bestSq = sq; best = other; }
        }
        return best;
    }

    private static boolean isArmed(LivingEntity entity) {
        Item item = entity.getMainHandItem().getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof BowItem || item instanceof CrossbowItem;
    }

    private static void flee(LivingEntity villager, LivingEntity threat) {
        Vec3 away = villager.position().subtract(threat.position());
        if (away.lengthSqr() < 1.0e-4) return;
        Vec3 to = villager.position().add(away.normalize().scale(8.0));
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(BlockPos.containing(to), 1.1f, 0));
    }
}
