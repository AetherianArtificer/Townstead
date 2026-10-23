package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Semantic storage roles declared by MCA building types in extended_buildings data. */
public final class BuildingStorageRoles {
    //? if >=1.21 {
    public static final ResourceLocation GENERAL =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "general");
    //?} else {
    /*public static final ResourceLocation GENERAL =
            new ResourceLocation(Townstead.MOD_ID, "general");
    *///?}

    private static volatile Map<String, Set<ResourceLocation>> ROLES = Map.of();

    private BuildingStorageRoles() {}

    public static void replaceAll(Map<String, Set<ResourceLocation>> next) {
        Map<String, Set<ResourceLocation>> stable = new LinkedHashMap<>();
        next.forEach((type, roles) -> stable.put(type, Set.copyOf(roles)));
        ROLES = Map.copyOf(stable);
    }

    public static Set<ResourceLocation> rolesFor(String buildingType) {
        return buildingType == null ? Set.of() : ROLES.getOrDefault(buildingType, Set.of());
    }

    public static boolean has(String buildingType, ResourceLocation role) {
        return role != null && rolesFor(buildingType).contains(role);
    }
}
