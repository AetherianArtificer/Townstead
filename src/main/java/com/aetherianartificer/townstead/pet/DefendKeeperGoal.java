package com.aetherianartificer.townstead.pet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** A kept animal goes for whatever just hurt its keeper, or whatever its keeper just attacked. */
final class DefendKeeperGoal extends TargetGoal {
    private static final int RECENT = 100;
    private final PathfinderMob animal;
    private @Nullable LivingEntity quarry;

    DefendKeeperGoal(PathfinderMob animal) {
        super(animal, false);
        this.animal = animal;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity keeper = VillagerPets.keeper(animal);
        if (keeper == null || PetDowned.isDowned(animal)) return false;
        LivingEntity attacker = keeper.getLastHurtByMob();
        if (attacker != null && keeper.tickCount - keeper.getLastHurtByMobTimestamp() < RECENT && fair(attacker, keeper)) {
            quarry = attacker;
            return true;
        }
        LivingEntity attacked = keeper.getLastHurtMob();
        if (attacked != null && keeper.tickCount - keeper.getLastHurtMobTimestamp() < RECENT && fair(attacked, keeper)) {
            quarry = attacked;
            return true;
        }
        return false;
    }

    private boolean fair(LivingEntity target, LivingEntity keeper) {
        if (target == animal || !target.isAlive()) return false;
        // Never turns on another animal of the same keeper.
        if (keeper.getUUID().equals(VillagerPets.keeperOf(target))) return false;
        return canAttack(target, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        animal.setTarget(quarry);
        super.start();
    }
}
