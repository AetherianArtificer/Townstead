package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.api.TownsteadAPI;
import com.aetherianartificer.townstead.api.TownsteadBuildingSnapshot;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/** Tests the Townstead/MCA building containing the entity's current position. */
public final class BuildingConditionType implements ConditionType {

    public static final String KEY = "pheno:building";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String expectedType = firstString(json, "building", "building_type");
        String expectedPrefix = firstString(json, "building_prefix", "type_prefix");
        int expectedId = GsonHelper.getAsInt(json, "id", Integer.MIN_VALUE);
        int expectedVillage = GsonHelper.getAsInt(json, "village", Integer.MIN_VALUE);
        int expectedVillageId = GsonHelper.getAsInt(json, "village_id", expectedVillage);
        int minSize = GsonHelper.getAsInt(json, "min_size", Integer.MIN_VALUE);
        int maxSize = GsonHelper.getAsInt(json, "max_size", Integer.MAX_VALUE);

        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel serverLevel)) return false;
            TownsteadBuildingSnapshot building = TownsteadAPI.buildingAt(serverLevel, ctx.pos());
            if (building == null) return false;
            if (expectedId != Integer.MIN_VALUE && building.id() != expectedId) return false;
            if (expectedVillageId != Integer.MIN_VALUE && building.villageId() != expectedVillageId) return false;
            if (building.size() < minSize || building.size() > maxSize) return false;
            if (expectedType != null && !matchesType(building.type(), expectedType)) return false;
            return expectedPrefix == null || (building.type() != null
                    && building.type().startsWith(expectedPrefix));
        };
    }

    private static String firstString(JsonObject json, String first, String second) {
        if (json.has(first) && !json.get(first).isJsonNull()) {
            String value = GsonHelper.getAsString(json, first, "").trim();
            return value.isEmpty() ? null : value;
        }
        if (json.has(second) && !json.get(second).isJsonNull()) {
            String value = GsonHelper.getAsString(json, second, "").trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private static boolean matchesType(String actual, String expected) {
        if (actual == null || expected == null) return false;
        if (actual.equals(expected)) return true;
        return !expected.contains(":") && slug(actual).equals(expected);
    }

    private static String slug(String id) {
        int index = id.indexOf(':');
        return index >= 0 ? id.substring(index + 1) : id;
    }
}
