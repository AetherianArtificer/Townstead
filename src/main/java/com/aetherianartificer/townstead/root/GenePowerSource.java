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
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.ROOTS) || !expresses(entity)) return;
        for (var allele : com.aetherianartificer.townstead.root.gene.GeneExpression.activeAlleles(entity)) {
            Gene gene = GeneRegistry.byId(allele.geneId());
            if (gene == null) continue;
            String variant = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(allele.variantId()).variant();
            var instance = gene.variants().stream().filter(v -> v.id().equals(variant))
                    .findFirst().orElse(gene.variants().get(0)).instance();
            out.add(new Power(gene.id(), instance));

        }
    }

    /**
     * Whether the entity expresses its species genes as live effects. Always for a villager/mob; for a
     * player only in MCA's full-genetics "Villager" model mode. In the "Player"/"Vanilla" model modes the
     * player is a plain player and its genes are inheritance data only (they still ride the genotype and
     * pass to children, this gate just stops them taking effect), so every pheno-routed gene effect
     * (attributes, modifiers, abilities, needs, buoyancy, immunity, reproduction...) switches off at once.
     * Server-authoritative: MCA persists the model choice in the player's save data. Mirrors the client
     * gate in {@code RootClientStore.expresses}. Defaults to expressing on any lookup failure.
     */
    private static boolean expresses(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return true;
        try {
            int model = net.conczin.mca.server.world.data.PlayerSaveData.get(player)
                    .getEntityData().getInt("PlayerModel");
            return model == net.conczin.mca.entity.VillagerLike.PlayerModel.VILLAGER.ordinal();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
