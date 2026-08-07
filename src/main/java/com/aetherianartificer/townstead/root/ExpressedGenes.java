package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.Genotype;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Resolves an entity's expressed (dominant) gene instances, uniform across
 * villagers (genotype on {@code Life}) and players (genotype on
 * {@link PlayerRoot}). The server-side counterpart to the client's per-entity
 * expressed-gene sync; consumed by the ability ticker, attribute applier and
 * damage handler.
 */
public final class ExpressedGenes {

    private ExpressedGenes() {}

    /** Only MCA villagers and players can carry Townstead's persisted genotype. */
    public static boolean canCarry(LivingEntity entity) {
        return entity instanceof VillagerEntityMCA || entity instanceof Player;
    }

    /** The entity's diploid genotype, or an empty one for entities that carry none. */
    public static Genotype genotypeOf(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            return TownsteadVillagers.get(villager).life().genotype();
        }
        if (entity instanceof Player player) {
            return PlayerRoot.getGenotype(player);
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
