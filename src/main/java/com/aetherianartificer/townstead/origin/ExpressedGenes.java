package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.Genotype;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Resolves an entity's expressed (dominant) gene instances, uniform across
 * villagers (genotype on {@code Life}) and players (genotype on
 * {@link PlayerOrigin}). The server-side counterpart to the client's per-entity
 * expressed-gene sync; consumed by the ability ticker, attribute applier and
 * damage handler.
 */
public final class ExpressedGenes {

    private ExpressedGenes() {}

    /** The entity's diploid genotype, or an empty one for entities that carry none. */
    public static Genotype genotypeOf(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            return TownsteadVillagers.get(villager).life().genotype();
        }
        if (entity instanceof Player player) {
            return PlayerOrigin.getGenotype(player);
        }
        return new Genotype();
    }

    /**
     * Expressed gene instances of the given type carried by the entity. Delegates to
     * the {@link com.aetherianartificer.townstead.pheno.power.Powers} facade so callers see
     * every source's components, not just genes.
     */
    public static <T extends GeneInstance> List<T> instancesOf(LivingEntity entity, Class<T> type) {
        return com.aetherianartificer.townstead.pheno.power.Powers.componentsOf(entity, type);
    }
}
