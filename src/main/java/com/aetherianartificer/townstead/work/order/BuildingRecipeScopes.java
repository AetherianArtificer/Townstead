package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.conczin.mca.entity.VillagerEntityMCA;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Building-authored narrowing for broad recipe catalogues at specialist venues. */
public final class BuildingRecipeScopes {
    private static volatile Map<String, Set<String>> NAMESPACES = Map.of();

    private BuildingRecipeScopes() {}

    public static void replaceAll(Map<String, Set<String>> next) {
        Map<String, Set<String>> stable = new LinkedHashMap<>();
        next.forEach((type, namespaces) -> stable.put(type, Set.copyOf(namespaces)));
        NAMESPACES = Map.copyOf(stable);
    }

    /** Missing scope means unrestricted; an authored scope admits only its named recipe mods. */
    public static boolean allows(String buildingType, ResourceLocation recipeId) {
        if (buildingType == null || !NAMESPACES.containsKey(buildingType)) return true;
        // Inline station protocols are authored physical capabilities, not registry families.
        // A Pizzeria may narrow a cutting board's enormous registry without hiding its own
        // Pizza Station, basin, or baking protocol merely because those synthetic ids are ours.
        if (recipeId != null && "townstead".equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith("protocol/")) return true;
        return recipeId != null
                && NAMESPACES.getOrDefault(buildingType, Set.of()).contains(recipeId.getNamespace());
    }

    /** The same rule for a worker's currently assigned building. */
    public static boolean allowsAssigned(ServerLevel level, VillagerEntityMCA villager,
                                         ResourceLocation recipeId) {
        var professionId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        var profession = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                .byId(professionId);
        if (profession == null) return true;
        String buildingType = com.aetherianartificer.townstead.profession.ProfessionSites
                .assignedSite(level, villager, profession)
                .map(site -> site.building() == null ? null : site.building().getType())
                .orElse(null);
        return allows(buildingType, recipeId);
    }
}
