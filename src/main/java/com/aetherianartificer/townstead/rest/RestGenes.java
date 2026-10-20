package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.gene.types.RestGeneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Reads the bearer's {@code pheno:rest} genes. */
public final class RestGenes {
    private RestGenes() {}

    /** How much a night in a bed restores, as a multiple of normal. */
    public static float recovery(LivingEntity entity) {
        float out = 1f;
        for (RestGeneType.Instance gene : Powers.componentsOf(entity, RestGeneType.Instance.class)) out *= gene.recovery();
        return out;
    }

    public static List<ResourceLocation> prefers(LivingEntity entity) {
        List<ResourceLocation> out = new ArrayList<>();
        for (RestGeneType.Instance gene : Powers.componentsOf(entity, RestGeneType.Instance.class)) out.addAll(gene.prefers());
        return out;
    }

    /** The gene keeping the bearer awake right now, or null when it may sleep. */
    public static @Nullable RestGeneType.Instance preventing(LivingEntity entity) {
        for (RestGeneType.Instance gene : Powers.componentsOf(entity, RestGeneType.Instance.class)) {
            if (gene.prevent() != null && gene.prevent().test(new ConditionContext(entity))) return gene;
        }
        return null;
    }
}
