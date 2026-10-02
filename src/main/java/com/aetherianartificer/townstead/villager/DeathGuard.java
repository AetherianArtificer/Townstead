package com.aetherianartificer.townstead.villager;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every way Townstead turns a death into something else: immortality, a gene that prevents death,
 * or being downed. Called from the death event, and from the start of MCA's own {@code die}, which
 * otherwise drops the villager's things, tells their family and moves them out even when the
 * death event is canceled.
 */
public final class DeathGuard {
    private DeathGuard() {}

    /** True when the entity survives; whatever saved them has already been applied. */
    public static boolean survives(LivingEntity entity, DamageSource source) {
        return com.aetherianartificer.townstead.root.Immortality.survivesDeath(entity, source)
                || com.aetherianartificer.townstead.root.prevent.Prevents.tryPreventDeath(entity)
                || com.aetherianartificer.townstead.downed.DownedService.onDeath(entity, source);
    }
}
