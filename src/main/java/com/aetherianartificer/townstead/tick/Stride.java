package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-interval check that fires on the same ticks as {@code gameTime % interval == 0}. For a
 * villager at reduced detail, whose brain may never run on those ticks, it also fires once an
 * interval has passed since it last fired.
 */
public final class Stride {
    private final int interval;
    private long last = Long.MIN_VALUE;

    public Stride(int interval) {
        this.interval = Math.max(1, interval);
    }

    public boolean due(long gameTime, VillagerEntityMCA villager) {
        if (last == Long.MIN_VALUE) last = gameTime;
        if (gameTime % interval == 0 || (gameTime - last >= interval && VillagerDetail.reduced(villager))) {
            last = gameTime;
            return true;
        }
        return false;
    }

    /**
     * The same check for a static ticker shared by every entity. Each entity keeps its own last
     * firing. {@code phase} is the value the check took the modulo of before, so it fires on
     * exactly the same ticks for anyone at full detail.
     */
    public static final class Site {
        private static final AtomicInteger NEXT = new AtomicInteger();
        private final int index = NEXT.getAndIncrement();
        private final int interval;

        public Site(int interval) {
            this.interval = Math.max(1, interval);
        }

        public boolean due(Entity entity, long phase) {
            return due(entity, phase, 0);
        }

        public boolean due(Entity entity, long phase, int remainder) {
            boolean hit = Math.floorMod(phase, interval) == remainder;
            if (!(entity instanceof VillagerEntityMCA villager)) return hit;
            EntityLocalCache cache = EntityLocalCache.of(villager);
            if (cache == null) return hit;
            long[] last = cache.strideLast;
            if (last == null || last.length <= index) {
                int from = last == null ? 0 : last.length;
                last = last == null ? new long[index + 1] : Arrays.copyOf(last, index + 1);
                Arrays.fill(last, from, last.length, Long.MIN_VALUE);
                cache.strideLast = last;
            }
            if (last[index] == Long.MIN_VALUE) last[index] = phase;
            if (hit || (phase - last[index] >= interval && VillagerDetail.reduced(villager))) {
                last[index] = phase;
                return true;
            }
            return false;
        }
    }
}
