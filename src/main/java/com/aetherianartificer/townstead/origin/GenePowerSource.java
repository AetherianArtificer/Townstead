package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The genetics {@link PowerSource}: an entity's expressed (dominant) genes become
 * powers keyed by gene id. The adapter lives in {@code origin} so the shared
 * {@code power} layer stays free of any genetics dependency.
 */
public final class GenePowerSource implements PowerSource {

    @Override
    public void collect(LivingEntity entity, List<Power> out) {
        for (Gene gene : Heredity.expressedGenes(ExpressedGenes.genotypeOf(entity))) {
            out.add(new Power(gene.id(), gene.instance()));
        }
    }
}
