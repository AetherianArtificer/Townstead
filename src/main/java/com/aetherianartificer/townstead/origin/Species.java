package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The body/base-model of a villager (Humanoid, and later Spider, Skeleton, …), and the
 * bottom layer of the genome. A species can grant {@code genes} shared by its ancestries
 * and lineages, and therefore by assignment profiles that select them (so species-wide
 * traits live here, not repeated per assignment profile). {@link #rig} is the model the
 * species renders as; the renderer reads its {@code base}.
 *
 * <p>Loaded from {@code data/<ns>/species/<path>.json}. {@code admixture_chance}
 * is the per-founder probability that a spawn of this species is a mixed-ancestry
 * blend of two or more of its origins instead of a single one (0 disables it).</p>
 */
public record Species(ResourceLocation id, Component displayName, Rig rig, Hold hold, float admixtureChance,
                      Genome genome) {
    public Species {
        hold = hold == null ? Hold.NONE : hold;
    }
}
