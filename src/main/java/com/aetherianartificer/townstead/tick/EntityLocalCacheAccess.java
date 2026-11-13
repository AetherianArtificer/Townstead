package com.aetherianartificer.townstead.tick;

/** Supplied by a LivingEntity mixin, so the cache lives and dies with the entity. */
public interface EntityLocalCacheAccess {
    EntityLocalCache townstead$localCache();
}
