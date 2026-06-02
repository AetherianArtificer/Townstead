package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The shape/base-model category of a villager (Humanoid, and later Ribbit,
 * Kobold, …). Species carries no genes — only identity and a {@code shape}
 * identifier reserved for future model selection. {@code humanoid} maps to MCA's
 * default villager model.
 *
 * <p>Loaded from {@code data/<ns>/species/<path>.json}. {@code admixture_chance}
 * is the per-founder probability that a spawn of this species is a mixed-ancestry
 * blend of two or more of its origins instead of a single one (0 disables it).</p>
 */
public record Species(ResourceLocation id, Component displayName, String shape, float admixtureChance) {
}
