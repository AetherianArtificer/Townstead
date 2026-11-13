package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerCacheAccess;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side query: is a given {@link Ability} currently active on an entity?
 * Passive abilities count whenever expressed; toggle abilities count only while
 * toggled on. Used by mixins that need to know an ability's live state (e.g. the
 * elytra-flight glide check).
 */
public final class Abilities {

    private Abilities() {}

    public static boolean isActive(LivingEntity entity, Ability ability) {
        long gameTime = Powers.cacheEpoch(entity);
        long revision = Powers.sourceRevision();
        PowerCacheAccess cache = entity instanceof PowerCacheAccess access ? access : null;
        long mask = cache == null ? Long.MIN_VALUE
                : cache.townstead$getCachedAbilityMask(gameTime, revision);
        if (mask == Long.MIN_VALUE) {
            mask = activeMask(entity);
            if (cache != null) cache.townstead$setCachedAbilityMask(gameTime, revision, mask);
        }
        return (mask & (1L << ability.ordinal())) != 0L;
    }

    // A condition can ask about abilities itself, and the answer it's after is the one being worked out.
    private static final ThreadLocal<Boolean> TESTING = ThreadLocal.withInitial(() -> false);

    private static boolean usable(AbilityGeneType.Instance instance, LivingEntity entity) {
        if (TESTING.get()) return true;
        TESTING.set(true);
        try {
            return instance.usable(entity);
        } finally {
            TESTING.set(false);
        }
    }

    private static long activeMask(LivingEntity entity) {
        long mask = 0L;
        for (Power gene : Powers.active(entity)) {
            if (!(gene.component() instanceof AbilityGeneType.Instance instance)) continue;
            if (!usable(instance, entity)) continue;
            if (instance.mode() == AbilityGeneType.Mode.TOGGLE) {
                if (AbilityToggles.isOn(entity, gene.id(), instance.defaultOn())) {
                    mask |= 1L << instance.ability().ordinal();
                }
            } else {
                mask |= 1L << instance.ability().ordinal();
            }
        }
        return mask;
    }
}
