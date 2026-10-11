package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.trait.TraitEffects;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Immortality: an immortal villager cannot die. This is the axis that distinguishes Immortal from
 * Ageless: ageless only freezes aging (still killable), immortal also prevents death. Immortal also
 * freezes aging (see {@code LifeStageProgression.isImmortal}), so immortal is effectively
 * ageless + deathless. Conferred by the heritable {@code townstead_roots:immortal} trait/gene (or
 * the legacy {@code Life.immortal} flag) — NOT by a potion (that is the Agelessness potion now).
 */
public final class Immortality {

    private Immortality() {}

    /** True when this villager is immortal (the trait/gene, or the legacy stored flag). */
    public static boolean isImmortal(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager).life().immortal() || TraitEffects.isImmortal(villager);
    }

    /**
     * A player is immortal when their root guarantees an immortal trait (a trait gene that always
     * occurs): what every founder of that root is born with. Players don't roll trait genes.
     */
    public static boolean isImmortal(net.minecraft.world.entity.player.Player player) {
        net.minecraft.resources.ResourceLocation rootId =
                net.minecraft.resources.ResourceLocation.tryParse(PlayerRoot.getRootId(player));
        if (rootId == null) return false;
        for (var inherited : RootRegistry.effectiveInheritedGenes(rootId)) {
            var gene = com.aetherianartificer.townstead.root.gene.GeneRegistry.byId(inherited.geneId());
            if (gene != null && gene.instance() instanceof com.aetherianartificer.townstead.root.gene.types.TraitOccurrenceGeneType.Instance trait
                    && trait.delta() >= 1f
                    && com.aetherianartificer.townstead.root.trait.TraitEffects.confersImmortality(trait.trait())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keep an immortal villager alive at 1 health when lethal damage would kill it, totem-style, and
     * report that death was prevented. Sources that bypass invulnerability (the void, {@code /kill})
     * still work, so an admin can always remove one. Called from the death event for villagers only.
     */
    public static boolean survivesDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide) return false;
        boolean immortal = entity instanceof VillagerEntityMCA villager ? isImmortal(villager)
                : entity instanceof net.minecraft.world.entity.player.Player player && isImmortal(player);
        if (!immortal) return false;
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (entity.getHealth() <= 0f) entity.setHealth(1f);
        return true;
    }
}
