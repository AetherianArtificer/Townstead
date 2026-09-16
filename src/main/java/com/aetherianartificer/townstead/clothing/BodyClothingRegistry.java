package com.aetherianartificer.townstead.clothing;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Per-node body clothing, one map per root-chain level, mirroring the hair policy registry. */
public final class BodyClothingRegistry {

    private static volatile Map<ResourceLocation, BodyClothing> SPECIES = Map.of();
    private static volatile Map<ResourceLocation, BodyClothing> ANCESTRY = Map.of();
    private static volatile Map<ResourceLocation, BodyClothing> LINEAGE = Map.of();
    private static volatile Map<ResourceLocation, BodyClothing> HERITAGE = Map.of();

    private BodyClothingRegistry() {}

    public static void setSpecies(Map<ResourceLocation, BodyClothing> next) { SPECIES = Map.copyOf(next); }
    public static void setAncestry(Map<ResourceLocation, BodyClothing> next) { ANCESTRY = Map.copyOf(next); }
    public static void setLineage(Map<ResourceLocation, BodyClothing> next) { LINEAGE = Map.copyOf(next); }
    public static void setHeritage(Map<ResourceLocation, BodyClothing> next) { HERITAGE = Map.copyOf(next); }

    public static BodyClothing species(@Nullable ResourceLocation id) { return get(SPECIES, id); }
    public static BodyClothing ancestry(@Nullable ResourceLocation id) { return get(ANCESTRY, id); }
    public static BodyClothing lineage(@Nullable ResourceLocation id) { return get(LINEAGE, id); }
    public static BodyClothing heritage(@Nullable ResourceLocation id) { return get(HERITAGE, id); }

    private static BodyClothing get(Map<ResourceLocation, BodyClothing> map, @Nullable ResourceLocation id) {
        if (id == null) return BodyClothing.INHERIT;
        BodyClothing value = map.get(id);
        return value == null ? BodyClothing.INHERIT : value;
    }
}
