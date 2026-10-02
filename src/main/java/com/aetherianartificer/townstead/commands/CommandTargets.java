package com.aetherianartificer.townstead.commands;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Picking the villager a command was meant for, when the caller named none. */
public final class CommandTargets {

    private static final double LOOK_RANGE = 16.0;

    private CommandTargets() {}

    /**
     * The villager the player is looking at, or failing that the nearest one in range.
     *
     * <p>Looking at someone is the intent a player expresses; proximity is the fallback for a
     * crowded room where the ray misses. Null when neither finds anybody.</p>
     */
    public static @Nullable VillagerEntityMCA lookedAtOrNearest(ServerPlayer player,
                                                               @Nullable VillagerEntityMCA exclude) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB sweep = new AABB(eye, eye.add(look.scale(LOOK_RANGE))).inflate(LOOK_RANGE);

        VillagerEntityMCA bestLook = null;
        double bestLookT = Double.POSITIVE_INFINITY;
        VillagerEntityMCA bestNear = null;
        double bestNearDist = Double.POSITIVE_INFINITY;

        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, sweep)) {
            if (villager == exclude) continue;
            double along = villager.position().subtract(eye).dot(look);
            if (along > 0 && along <= LOOK_RANGE) {
                double perp = villager.position().distanceTo(eye.add(look.scale(along)));
                if (perp <= Math.max(0.7, villager.getBbWidth()) && along < bestLookT) {
                    bestLookT = along;
                    bestLook = villager;
                }
            }
            double dist = player.distanceToSqr(villager);
            if (dist < bestNearDist) {
                bestNearDist = dist;
                bestNear = villager;
            }
        }
        if (bestLook != null) return bestLook;
        return bestNear != null && bestNearDist <= LOOK_RANGE * LOOK_RANGE ? bestNear : null;
    }
}
