package com.aetherianartificer.townstead.tick;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-entity slots for lookups that would otherwise go through a shared table every tick. Each
 * slot holds the exact object the table holds, tagged with the owner's UUID and the table's
 * removal epoch, so a removal or a UUID change sends the next read back to the table.
 */
public final class EntityLocalCache {
    public Object stateMemo;
    public long stateMemoTime = Long.MIN_VALUE;

    public Object statePrevious;
    public long statePreviousEpoch = -1;
    public UUID statePreviousOwner;

    public Object stateNextPeriodic;
    public long stateNextPeriodicEpoch = -1;
    public UUID stateNextPeriodicOwner;

    public Object villagerState;
    public long villagerStateEpoch = -1;
    public UUID villagerStateOwner;

    public int detailInterval = 1;
    public long detailCheckAt = Long.MIN_VALUE;
    public long detailTick = Long.MIN_VALUE;
    public boolean detailDue = true;
    public boolean detailResting;
    public boolean detailFull;

    public long[] strideLast;

    public static @Nullable EntityLocalCache of(Entity entity) {
        return entity instanceof EntityLocalCacheAccess access ? access.townstead$localCache() : null;
    }
}
