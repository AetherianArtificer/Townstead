package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.tick.EntityLocalCache;
import com.aetherianartificer.townstead.tick.EntityLocalCacheAccess;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLocalCacheMixin implements EntityLocalCacheAccess {

    @Unique private EntityLocalCache townstead$localCache;

    @Override
    public EntityLocalCache townstead$localCache() {
        EntityLocalCache cache = townstead$localCache;
        if (cache == null) townstead$localCache = cache = new EntityLocalCache();
        return cache;
    }
}
