package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.trait.TraitEffects;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Immortality: an immortal villager cannot die. This is the axis that distinguishes Immortal from
 * Ageless: ageless only freezes aging (still killable), immortal also prevents death. Immortal also
 * freezes aging (see {@code LifeStageProgression.isImmortal}), so immortal is effectively
 * ageless + deathless. Conferred by the heritable {@code townstead_origins:immortal} trait/gene (or
 * the legacy {@code Life.immortal} flag) — NOT by a potion (that is the Agelessness potion now).
 */
public final class Immortality {

    private Immortality() {}

    /** True when this villager is immortal (the trait/gene, or the legacy stored flag). */
    public static boolean isImmortal(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager).life().immortal() || TraitEffects.isImmortal(villager);
    }

    /**
     * Keep an immortal villager alive at 1 health when lethal damage would kill it, totem-style, and
     * report that death was prevented. Sources that bypass invulnerability (the void, {@code /kill})
     * still work, so an admin can always remove one. Called from the death event for villagers only.
     */
    public static boolean survivesDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide) return false;
        if (!(entity instanceof VillagerEntityMCA villager) || !isImmortal(villager)) return false;
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (entity.getHealth() <= 0f) entity.setHealth(1f);
        return true;
    }
}
