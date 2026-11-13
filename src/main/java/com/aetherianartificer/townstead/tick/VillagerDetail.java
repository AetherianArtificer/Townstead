package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lower detail for villagers far from every player. A far villager ticks its brain and Townstead
 * steps every few ticks, and while it stands idle it also skips movement between those ticks.
 * Anything that needs a quick answer (harm, danger, a player at hand) puts it back to every tick.
 */
public final class VillagerDetail {
    static final int NEAR_BLOCKS = 16;
    static final int MAX_INTERVAL = 20;
    static final int DISTANCE_SHIFT = 8;
    private static final int REFRESH_TICKS = 20;
    private static final double IDLE_HORIZONTAL_SPEED_SQ = 1.0E-4;
    // Measurement only: "x,y,z" counts as a player standing there, for labs that run with nobody on.
    private static final @Nullable Vec3 ANCHOR = parseAnchor(System.getProperty("townstead.detailAnchor"));

    private VillagerDetail() {}

    /** Ticks between brain ticks at this squared distance to the nearest player; infinite means none. */
    static int intervalFor(double nearestDistanceSq) {
        if (nearestDistanceSq <= (double) NEAR_BLOCKS * NEAR_BLOCKS) return 1;
        if (Double.isInfinite(nearestDistanceSq) || Double.isNaN(nearestDistanceSq)) return MAX_INTERVAL;
        long scaled = (long) nearestDistanceSq >> DISTANCE_SHIFT;
        return (int) Math.max(1L, Math.min(MAX_INTERVAL, scaled));
    }

    /** Whether this tick runs the brain and Townstead's per-villager steps. */
    public static boolean brainDue(VillagerEntityMCA villager) {
        EntityLocalCache cache = current(villager);
        return cache == null || cache.detailDue;
    }

    /** Whether this villager is ticking its brain less often than every tick right now. */
    public static boolean reduced(@Nullable VillagerEntityMCA villager) {
        if (villager == null) return false;
        EntityLocalCache cache = current(villager);
        return cache != null && cache.detailInterval > 1 && !cache.detailFull;
    }

    /** Whether this tick may skip movement and entity pushing. */
    public static boolean movementResting(VillagerEntityMCA villager) {
        EntityLocalCache cache = current(villager);
        return cache != null && cache.detailResting;
    }

    private static @Nullable EntityLocalCache current(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return null;
        EntityLocalCache cache = EntityLocalCache.of(villager);
        if (cache == null) return null;
        long now = level.getGameTime();
        if (cache.detailTick == now) return cache;
        cache.detailTick = now;
        int interval = 1;
        cache.detailFull = false;
        if (!Switchboard.get(TownsteadConfig.FAR_VILLAGER_DETAIL)) {
            cache.detailInterval = 1;
        } else {
            if (now >= cache.detailCheckAt) {
                cache.detailInterval = farInterval(level, villager);
                cache.detailCheckAt = now + REFRESH_TICKS;
            }
            cache.detailFull = cache.detailInterval > 1 && needsFullDetail(villager);
            if (cache.detailInterval > 1 && !cache.detailFull) interval = cache.detailInterval;
        }
        cache.detailDue = interval <= 1 || Math.floorMod(now + villager.getId(), interval) == 0;
        cache.detailResting = !cache.detailDue && idle(villager);
        return cache;
    }

    private static int farInterval(ServerLevel level, VillagerEntityMCA villager) {
        double nearest = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            nearest = Math.min(nearest, player.distanceToSqr(villager));
        }
        if (ANCHOR != null) nearest = Math.min(nearest, ANCHOR.distanceToSqr(villager.position()));
        if (nearest > (double) NEAR_BLOCKS * NEAR_BLOCKS && level.isRaided(villager.blockPosition())) return 1;
        return intervalFor(nearest);
    }

    private static boolean needsFullDetail(VillagerEntityMCA villager) {
        if (villager.hurtTime > 0 || villager.isOnFire() || villager.isInWaterOrBubble() || villager.isInLava()) return true;
        if (villager.isPassenger() || villager.isVehicle() || villager.isLeashed()) return true;
        if (villager.getTradingPlayer() != null || villager.getInteractions().getInteractingPlayer().isPresent()) return true;
        Brain<?> brain = villager.getBrain();
        return brain.isActive(Activity.PANIC)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    private static boolean idle(VillagerEntityMCA villager) {
        if (!villager.onGround() || villager.isSleeping()) return villager.isSleeping();
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) return false;
        if (!villager.getNavigation().isDone()) return false;
        return villager.getDeltaMovement().horizontalDistanceSqr() < IDLE_HORIZONTAL_SPEED_SQ;
    }

    private static @Nullable Vec3 parseAnchor(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 3) return null;
        try {
            return new Vec3(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
