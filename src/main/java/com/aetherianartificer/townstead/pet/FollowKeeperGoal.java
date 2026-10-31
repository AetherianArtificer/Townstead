package com.aetherianartificer.townstead.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A kept animal follows its keeper while they are up and about, and catches up by teleporting
 * when it falls far behind. While the keeper sleeps it stays where it is.
 */
final class FollowKeeperGoal extends Goal {
    private static final double START = 8, STOP = 3, TELEPORT = 24;
    private final PathfinderMob animal;
    private @Nullable LivingEntity keeper;
    private int repath;

    FollowKeeperGoal(PathfinderMob animal) {
        this.animal = animal;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity found = VillagerPets.keeper(animal);
        if (found == null || found.isSleeping() || animal.isLeashed() || animal.isPassenger() || PetDowned.isDowned(animal)) return false;
        if (animal.distanceToSqr(found) < START * START) return false;
        keeper = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return keeper != null && keeper.isAlive() && !keeper.isSleeping() && !PetDowned.isDowned(animal) && !animal.getNavigation().isDone()
                && animal.distanceToSqr(keeper) > STOP * STOP;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        keeper = null;
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (keeper == null) return;
        animal.getLookControl().setLookAt(keeper, 10f, animal.getMaxHeadXRot());
        if (--repath > 0) return;
        repath = 10;
        if (animal.distanceToSqr(keeper) >= TELEPORT * TELEPORT) {
            teleportNear(keeper);
            return;
        }
        animal.getNavigation().moveTo(keeper, 1.2);
    }

    private void teleportNear(LivingEntity keeper) {
        BlockPos base = keeper.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = animal.getRandom().nextInt(7) - 3, dz = animal.getRandom().nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos pos = base.offset(dx, 0, dz);
            if (!animal.level().getBlockState(pos.below()).isSolidRender(animal.level(), pos.below())) continue;
            if (!animal.level().noCollision(animal, animal.getBoundingBox().move(pos.getX() + 0.5 - animal.getX(),
                    pos.getY() - animal.getY(), pos.getZ() + 0.5 - animal.getZ()))) continue;
            animal.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, animal.getYRot(), animal.getXRot());
            animal.getNavigation().stop();
            return;
        }
    }
}
