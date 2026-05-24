package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The shape/base-model category of a villager (Humanoid, and later Ribbit,
 * Kobold, …). Species carries no genes — only identity and a {@code shape}
 * identifier reserved for future model selection. {@code humanoid} maps to MCA's
 * default villager model.
 *
 * <p>Loaded from {@code data/<ns>/species/<path>.json}.</p>
 */
public record Species(ResourceLocation id, Component displayName, String shape) {
}
