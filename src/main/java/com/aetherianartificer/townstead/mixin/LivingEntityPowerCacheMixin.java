package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerCacheAccess;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/** Entity-owned, one-game-tick cache for resolved powers and active abilities. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPowerCacheMixin implements PowerCacheAccess {

    @Unique private long townstead$powerTick = Long.MIN_VALUE;
    @Unique private long townstead$powerRevision = Long.MIN_VALUE;
    @Unique private List<Power> townstead$powers;
    @Unique private long townstead$abilityTick = Long.MIN_VALUE;
    @Unique private long townstead$abilityRevision = Long.MIN_VALUE;
    @Unique private long townstead$abilityMask;

    @Override
    public List<Power> townstead$getCachedPowers(long gameTime, long sourceRevision) {
        return townstead$powerTick == gameTime && townstead$powerRevision == sourceRevision
                ? townstead$powers : null;
    }

    @Override
    public void townstead$setCachedPowers(long gameTime, long sourceRevision, List<Power> powers) {
        townstead$powerTick = gameTime;
        townstead$powerRevision = sourceRevision;
        townstead$powers = powers;
    }

    @Override
    public long townstead$getCachedAbilityMask(long gameTime, long sourceRevision) {
        return townstead$abilityTick == gameTime && townstead$abilityRevision == sourceRevision
                ? townstead$abilityMask : Long.MIN_VALUE;
    }

    @Override
    public void townstead$setCachedAbilityMask(long gameTime, long sourceRevision, long mask) {
        townstead$abilityTick = gameTime;
        townstead$abilityRevision = sourceRevision;
        townstead$abilityMask = mask;
    }

    @Override
    public void townstead$invalidatePowerCache() {
        townstead$powerTick = Long.MIN_VALUE;
        townstead$abilityTick = Long.MIN_VALUE;
        townstead$powers = null;
    }
}
