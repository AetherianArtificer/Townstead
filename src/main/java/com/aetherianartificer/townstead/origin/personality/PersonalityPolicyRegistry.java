package com.aetherianartificer.townstead.origin.personality;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The {@link Personalities} allow/deny policy declared by each origin-tree node, indexed per level so
 * a node id shared across levels (e.g. an origin and species both named {@code skeletownies}) can't
 * collide. Each origin loader registers the policies it parsed; {@link PersonalityResolver} reads
 * them back when rolling a personality. Kept beside the records (rather than on them) so the four
 * record constructors stay untouched.
 */
public final class PersonalityPolicyRegistry {

    private static volatile Map<ResourceLocation, Personalities> SPECIES = Map.of();
    private static volatile Map<ResourceLocation, Personalities> ANCESTRY = Map.of();
    private static volatile Map<ResourceLocation, Personalities> LINEAGE = Map.of();
    private static volatile Map<ResourceLocation, Personalities> ORIGIN = Map.of();

    private PersonalityPolicyRegistry() {}

    public static void setSpecies(Map<ResourceLocation, Personalities> next) { SPECIES = Map.copyOf(next); }
    public static void setAncestry(Map<ResourceLocation, Personalities> next) { ANCESTRY = Map.copyOf(next); }
    public static void setLineage(Map<ResourceLocation, Personalities> next) { LINEAGE = Map.copyOf(next); }
    public static void setOrigin(Map<ResourceLocation, Personalities> next) { ORIGIN = Map.copyOf(next); }

    // get(null) throws on an immutable Map.copyOf (null.hashCode()), and tree nodes legitimately have
    // a null lineage/ancestry/species, so null-guard before every lookup.
    public static Personalities species(ResourceLocation id) { return id == null ? Personalities.EMPTY : SPECIES.getOrDefault(id, Personalities.EMPTY); }
    public static Personalities ancestry(ResourceLocation id) { return id == null ? Personalities.EMPTY : ANCESTRY.getOrDefault(id, Personalities.EMPTY); }
    public static Personalities lineage(ResourceLocation id) { return id == null ? Personalities.EMPTY : LINEAGE.getOrDefault(id, Personalities.EMPTY); }
    public static Personalities origin(ResourceLocation id) { return id == null ? Personalities.EMPTY : ORIGIN.getOrDefault(id, Personalities.EMPTY); }
}
