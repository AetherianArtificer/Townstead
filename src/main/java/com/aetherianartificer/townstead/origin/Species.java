package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The shape/base-model category of a villager (Humanoid, and later Ribbit,
 * Kobold, …), and the bottom layer of the genome. A species can grant
 * {@code genes} shared by its ancestries and lineages, and therefore by
 * assignment profiles that select them (so
 * species-wide traits — a Ribbit's swimming, no-armor build — live here, not
 * repeated per assignment profile). {@code shape} is an identifier reserved for model
 * selection; {@code humanoid} maps to MCA's default villager model.
 *
 * <p>Loaded from {@code data/<ns>/species/<path>.json}. {@code admixture_chance}
 * is the per-founder probability that a spawn of this species is a mixed-ancestry
 * blend of two or more of its origins instead of a single one (0 disables it).</p>
 */
public record Species(ResourceLocation id, Component displayName, String shape, float admixtureChance,
                      Genome genome) {
}
