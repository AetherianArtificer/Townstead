package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.types.BuoyancyGeneType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves whether a {@code buoyancy} gene should nullify the fluid the bearer is
 * currently standing in. Read from the {@code isAffectedByFluids} mixins on both
 * sides so the controlling client predicts the same land-movement underwater as the
 * server (a server-only decision on a player would be overridden, since players are
 * movement-authoritative). Server reads the authoritative genes; the client reads the
 * synced catalog mirror ({@code ClientBuoyancy}).
 */
public final class Buoyancy {

    private Buoyancy() {}

    /** True when the entity is in a fluid its genes mark as nullified, so vanilla treats it as not in fluid. */
    public static boolean ignoresCurrentFluid(LivingEntity entity) {
        if (!entity.isInWater() && !entity.isInLava()) return false;
        if (!ExpressedGenes.canCarry(entity)) return false;
        List<TagKey<Fluid>> fluids = entity.level().isClientSide
                ? com.aetherianartificer.townstead.client.root.ClientBuoyancy.ignoredFluids(entity)
                : serverFluids(entity);
        for (TagKey<Fluid> fluid : fluids) {
            if (entity.getFluidHeight(fluid) > 0) return true;
        }
        return false;
    }

    private static List<TagKey<Fluid>> serverFluids(LivingEntity entity) {
        List<BuoyancyGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, BuoyancyGeneType.Instance.class);
        if (genes.isEmpty()) return List.of();
        List<TagKey<Fluid>> out = new ArrayList<>();
        for (BuoyancyGeneType.Instance gene : genes) out.addAll(gene.fluids());
        return out;
    }
}
