package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reload-safe public view of the active provider provenance behind composed Careers. */
public final class CareerProviders {

    private static volatile Map<CareerProviderContributions.Target, List<ResourceLocation>> ACTIVE = Map.of();

    private CareerProviders() {}

    static void replaceAll(Map<CareerProviderContributions.Target, List<ResourceLocation>> next) {
        Map<CareerProviderContributions.Target, List<ResourceLocation>> copy = new LinkedHashMap<>();
        next.forEach((target, sources) -> copy.put(target, List.copyOf(sources)));
        ACTIVE = Map.copyOf(copy);
    }

    public static List<ResourceLocation> sources(ResourceLocation profession, String path) {
        return ACTIVE.getOrDefault(new CareerProviderContributions.Target(profession, path), List.of());
    }

    public static Map<CareerProviderContributions.Target, List<ResourceLocation>> all() {
        return ACTIVE;
    }
}
