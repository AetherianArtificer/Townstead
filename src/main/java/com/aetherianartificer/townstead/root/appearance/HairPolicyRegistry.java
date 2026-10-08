package com.aetherianartificer.townstead.root.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Data-pack hair declarations indexed separately for each identity level. */
public final class HairPolicyRegistry {

    private static volatile Map<ResourceLocation, HairPolicy> SPECIES = Map.of();
    private static volatile Map<ResourceLocation, HairPolicy> ANCESTRY = Map.of();
    private static volatile Map<ResourceLocation, HairPolicy> LINEAGE = Map.of();
    private static volatile Map<ResourceLocation, HairPolicy> HERITAGE = Map.of();

    private HairPolicyRegistry() {}

    public static void setSpecies(Map<ResourceLocation, HairPolicy> next) { SPECIES = Map.copyOf(next); }
    public static void setAncestry(Map<ResourceLocation, HairPolicy> next) { ANCESTRY = Map.copyOf(next); }
    public static void setLineage(Map<ResourceLocation, HairPolicy> next) { LINEAGE = Map.copyOf(next); }
    public static void setHeritage(Map<ResourceLocation, HairPolicy> next) { HERITAGE = Map.copyOf(next); }

    public static HairPolicy species(ResourceLocation id) { return get(SPECIES, id); }
    public static HairPolicy ancestry(ResourceLocation id) { return get(ANCESTRY, id); }
    public static HairPolicy lineage(ResourceLocation id) { return get(LINEAGE, id); }
    public static HairPolicy heritage(ResourceLocation id) { return get(HERITAGE, id); }

    private static HairPolicy get(Map<ResourceLocation, HairPolicy> policies, ResourceLocation id) {
        return id == null ? HairPolicy.INHERIT : policies.getOrDefault(id, HairPolicy.INHERIT);
    }
}
