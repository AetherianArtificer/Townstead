package com.aetherianartificer.townstead.origin.effect;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.StackingEffectGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
//?} else {
/*import net.minecraft.world.effect.MobEffect;
*///?}

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-entity stack state for {@code stacking_effect} genes (transient, like cooldowns;
 * resets on reload). On each pass the counter climbs while the gene's condition holds and
 * falls when it doesn't, and the gene's effects are applied at amplifier
 * {@code stacks / stacks_per_level}. Driven from the passive ability ticker.
 */
public final class StackingEffects {

    private static final int STEP = 10;            // matches the ability ticker throttle
    private static final int EFFECT_DURATION = 30; // re-applied each pass

    private static final Map<UUID, Map<ResourceLocation, Integer>> STACKS = new ConcurrentHashMap<>();

    private StackingEffects() {}

    public static void apply(LivingEntity entity, ResourceLocation geneId,
                             StackingEffectGeneType.Instance gene, ConditionContext ctx) {
        if (entity.level().isClientSide) return;
        boolean active = gene.condition() == null || gene.condition().test(ctx);
        int stacks = get(entity, geneId) + (active ? STEP : -STEP);
        stacks = Math.max(gene.minStacks(), Math.min(gene.maxStacks(), stacks));
        STACKS.computeIfAbsent(entity.getUUID(), k -> new ConcurrentHashMap<>()).put(geneId, stacks);
        if (stacks <= 0) return;
        int amplifier = Math.min(255, stacks / gene.stacksPerLevel());
        for (StackingEffectGeneType.EffectSpec spec : gene.effects()) {
            addEffect(entity, spec, amplifier);
        }
    }

    public static void clear(UUID uuid) {
        STACKS.remove(uuid);
    }

    private static int get(LivingEntity entity, ResourceLocation geneId) {
        Map<ResourceLocation, Integer> map = STACKS.get(entity.getUUID());
        return map != null ? map.getOrDefault(geneId, 0) : 0;
    }

    private static void addEffect(LivingEntity entity, StackingEffectGeneType.EffectSpec spec, int amplifier) {
        //? if neoforge {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, spec.effect())).orElse(null);
        //?} else {
        /*MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(spec.effect());
        *///?}
        if (effect == null) return;
        entity.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, amplifier,
                spec.ambient(), spec.particles(), spec.icon()));
    }
}
