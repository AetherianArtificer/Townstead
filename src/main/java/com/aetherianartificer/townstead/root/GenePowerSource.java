package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The genetics {@link PowerSource}: an entity's expressed (dominant) genes become
 * powers keyed by gene id. The adapter lives in {@code origin} so the shared
 * {@code power} layer stays free of any genetics dependency.
 */
public final class GenePowerSource implements PowerSource {

    @Override
    public boolean supports(LivingEntity entity) {
        return ExpressedGenes.canCarry(entity);
    }

    @Override
    public void collect(LivingEntity entity, List<Power> out) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.ROOTS)) return;
        for (var allele : com.aetherianartificer.townstead.root.gene.GeneExpression.activeAlleles(entity)) {
            Gene gene = GeneRegistry.byId(allele.geneId());
            if (gene == null) continue;
            String variant = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(allele.variantId()).variant();
            var instance = gene.variants().stream().filter(v -> v.id().equals(variant))
                    .findFirst().orElse(gene.variants().get(0)).instance();
            out.add(new Power(gene.id(), instance));

        }
    }
}
