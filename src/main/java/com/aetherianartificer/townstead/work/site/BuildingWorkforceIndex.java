package com.aetherianartificer.townstead.work.site;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which professions a building explicitly accepts as visiting workers.
 *
 * <p>The declaration belongs to {@code extended_buildings/<building type>.json}, because a place
 * knows which trades it was built to host. Profession defs continue to own capabilities and
 * recipe filters; this index only makes the connection between a compatible worker and a place.
 * A missing declaration preserves the older primary-POI ownership rules.</p>
 */
public final class BuildingWorkforceIndex {
    private static volatile Map<String, List<ResourceLocation>> WORKERS = Map.of();

    private BuildingWorkforceIndex() {}

    public static void replaceAll(Map<String, List<ResourceLocation>> next) {
        Map<String, List<ResourceLocation>> stable = new LinkedHashMap<>();
        next.forEach((type, professions) -> stable.put(type, List.copyOf(professions)));
        WORKERS = Map.copyOf(stable);
    }

    /** Whether the building authored a workforce declaration, including an intentionally empty one. */
    public static boolean defines(String buildingType) {
        return buildingType != null && WORKERS.containsKey(buildingType);
    }

    public static List<ResourceLocation> professionsFor(String buildingType) {
        return buildingType == null ? List.of() : WORKERS.getOrDefault(buildingType, List.of());
    }

    public static boolean accepts(String buildingType, ResourceLocation profession) {
        if (buildingType == null || profession == null) return false;
        for (ResourceLocation accepted : professionsFor(buildingType)) {
            if (com.aetherianartificer.townstead.profession.ProfessionIdentity
                    .matches(null, profession, accepted)) return true;
        }
        return false;
    }

    /** Path-aware worker acceptance for compatibility professions such as Cook/Chef. */
    public static boolean accepts(String buildingType, LivingEntity worker) {
        if (buildingType == null || worker == null) return false;
        for (ResourceLocation accepted : professionsFor(buildingType)) {
            if (com.aetherianartificer.townstead.profession.ProfessionIdentity
                    .matches(worker, accepted)) return true;
        }
        return false;
    }
}
