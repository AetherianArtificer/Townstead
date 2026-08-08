package com.aetherianartificer.townstead.pheno.power;

import java.util.List;

/**
 * Entity-local cache storage supplied by a LivingEntity mixin. Keeping the cache on
 * the entity makes its lifetime match the entity and avoids a global map retaining
 * unloaded entities.
 */
public interface PowerCacheAccess {

    List<Power> townstead$getCachedPowers(long gameTime, long sourceRevision);

    void townstead$setCachedPowers(long gameTime, long sourceRevision, List<Power> powers);

    long townstead$getCachedAbilityMask(long gameTime, long sourceRevision);

    void townstead$setCachedAbilityMask(long gameTime, long sourceRevision, long mask);

    void townstead$invalidatePowerCache();
}
