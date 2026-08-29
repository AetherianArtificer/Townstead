package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A profession's optional ordering of semantic external storage-building roles. */
public record StoragePreference(List<ResourceLocation> preferredRoles) {
    public static final StoragePreference NONE = new StoragePreference(List.of());
    public static final int LOCAL_RANK = 0;
    public static final int EXTERNAL_BASE_RANK = 1;
    public static final int FALLBACK_RANK = Integer.MAX_VALUE;

    public StoragePreference {
        preferredRoles = List.copyOf(preferredRoles);
    }

    /**
     * Local worksite storage is always rank zero. A matching preferred role ranks next, followed
     * by a general store. Buildings with neither are not part of the external storage route.
     */
    public int buildingRank(String buildingType) {
        Set<ResourceLocation> roles = BuildingStorageRoles.rolesFor(buildingType);
        for (int i = 0; i < preferredRoles.size(); i++) {
            if (roles.contains(preferredRoles.get(i))) return EXTERNAL_BASE_RANK + i;
        }
        if (roles.contains(BuildingStorageRoles.GENERAL)) {
            return EXTERNAL_BASE_RANK + preferredRoles.size();
        }
        return FALLBACK_RANK;
    }

    public static StoragePreference forVillager(VillagerEntityMCA villager) {
        if (villager == null) return NONE;
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        ProfessionDef def = id == null ? null : ProfessionDefs.byId(id);
        return def == null ? NONE : def.storage();
    }

    /** Parses {@code "storage":{"preferred_roles":["townstead:materials"]}}. */
    public static StoragePreference parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("'storage' must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("buildings") || object.has("preferred")) {
            throw new IllegalArgumentException(
                    "Container blocks/building names are not storage preferences; use 'preferred_roles'");
        }
        List<ResourceLocation> roles = new ArrayList<>();
        if (object.has("preferred_roles")) {
            if (!object.get("preferred_roles").isJsonArray()) {
                throw new IllegalArgumentException("'storage.preferred_roles' must be an array");
            }
            for (JsonElement entry : object.getAsJsonArray("preferred_roles")) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "'storage.preferred_roles' entries must be resource ids");
                }
                ResourceLocation role = ResourceLocation.tryParse(entry.getAsString());
                if (role == null) {
                    throw new IllegalArgumentException("Invalid storage role '" + entry.getAsString() + "'");
                }
                if (!roles.contains(role)) roles.add(role);
            }
        }
        return roles.isEmpty() ? NONE : new StoragePreference(roles);
    }
}
