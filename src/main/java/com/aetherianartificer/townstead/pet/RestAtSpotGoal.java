package com.aetherianartificer.townstead.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * While its keeper sleeps or is away, a kept animal goes to its rest spot and settles there. It
 * gets up again as soon as the keeper is up and nearby.
 */
final class RestAtSpotGoal extends Goal {
    private static final double ARRIVED = 1.5;
    private final PathfinderMob animal;
    private @Nullable BlockPos spot;
    private int repath;

    RestAtSpotGoal(PathfinderMob animal) {
        this.animal = animal;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (animal.isLeashed() || animal.isPassenger() || PetDowned.isDowned(animal)) return false;
        spot = VillagerPets.rest(animal);
        return spot != null && keeperResting();
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && keeperResting() && !PetDowned.isDowned(animal);
    }

    /** The keeper is asleep, or not here at all (away on a journey, or unloaded). */
    private boolean keeperResting() {
        LivingEntity keeper = VillagerPets.keeper(animal);
        return keeper == null || keeper.isSleeping();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        settle(false);
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (spot == null) return;
        boolean there = animal.blockPosition().distToCenterSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) <= ARRIVED * ARRIVED;
        if (there) {
            animal.getNavigation().stop();
            settle(true);
            return;
        }
        settle(false);
        if (--repath > 0) return;
        repath = 20;
        animal.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
    }

    private void settle(boolean down) {
        if (animal instanceof TamableAnimal tamable && tamable.isInSittingPose() != down) tamable.setInSittingPose(down);
    }
}
