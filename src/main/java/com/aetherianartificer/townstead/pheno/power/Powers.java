package com.aetherianartificer.townstead.pheno.power;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The source-agnostic facade every behavior applier reads through. Sources register at
 * startup (genetics now, professions later); {@link #active} unions them for an entity.
 * Today only {@code GenePowerSource} is registered, so behavior is identical to reading
 * expressed genes directly, but the seam means a new source needs no applier changes.
 */
public final class Powers {

    // Expressed powers only change on explicit genotype/model edits or data reload.
    // A short epoch also bounds staleness for changes made by MCA outside Townstead.
    private static final int CACHE_INTERVAL_TICKS = 20;
    private static final List<PowerSource> SOURCES = new CopyOnWriteArrayList<>();
    private static final AtomicLong SOURCE_REVISION = new AtomicLong();

    private Powers() {}

    public static void register(PowerSource source) {
        SOURCES.add(source);
        SOURCE_REVISION.incrementAndGet();
    }

    /** Every power granted to {@code entity} across all sources. */
    public static List<Power> active(LivingEntity entity) {
        long gameTime = cacheEpoch(entity);
        long revision = SOURCE_REVISION.get();
        PowerCacheAccess cache = entity instanceof PowerCacheAccess access ? access : null;
        if (cache != null) {
            List<Power> cached = cache.townstead$getCachedPowers(gameTime, revision);
            if (cached != null) return cached;
        }

        List<Power> out = null;
        for (PowerSource source : SOURCES) {
            if (!source.supports(entity)) continue;
            if (out == null) out = new ArrayList<>();
            source.collect(entity, out);
        }
        if (out == null) out = List.of();
        if (cache != null) cache.townstead$setCachedPowers(gameTime, revision, out);
        return out;
    }

    public static long sourceRevision() {
        return SOURCE_REVISION.get();
    }

    /** Invalidate all entity caches after gene definitions are atomically replaced. */
    public static void dataReloaded() {
        SOURCE_REVISION.incrementAndGet();
    }

    public static long cacheEpoch(LivingEntity entity) {
        return entity.level().getGameTime() / CACHE_INTERVAL_TICKS;
    }

    /** Invalidate this entity after a same-tick genotype or toggle mutation. */
    public static void invalidate(LivingEntity entity) {
        if (entity instanceof PowerCacheAccess cache) cache.townstead$invalidatePowerCache();
    }

    /** The components of the given type granted to {@code entity} (id-less convenience). */
    public static <T> List<T> componentsOf(LivingEntity entity, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Power power : active(entity)) {
            if (type.isInstance(power.component())) out.add(type.cast(power.component()));
        }
        return out;
    }
}
