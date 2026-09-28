package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.Optional;

/**
 * Tests the MCA/Townstead village resolved at the entity's current position. Besides id, name,
 * building and population bounds: {@code min_free_beds} counts beds nobody lives in yet, and
 * {@code player_founded} tells a village a player started from one that was already there.
 * <pre>{ "type": "pheno:village", "player_founded": false, "min_free_beds": 2 }</pre>
 */
public final class VillageConditionType implements ConditionType {

    public static final String KEY = "pheno:village";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        int expectedId = GsonHelper.getAsInt(json, "id", Integer.MIN_VALUE);
        int expectedVillage = GsonHelper.getAsInt(json, "village", Integer.MIN_VALUE);
        int expectedVillageId = GsonHelper.getAsInt(json, "village_id", expectedVillage);
        String expectedName = firstString(json, "name", "village_name");
        int minBuildings = GsonHelper.getAsInt(json, "min_buildings", Integer.MIN_VALUE);
        int maxBuildings = GsonHelper.getAsInt(json, "max_buildings", Integer.MAX_VALUE);
        int minPopulation = GsonHelper.getAsInt(json, "min_population", Integer.MIN_VALUE);
        int maxPopulation = GsonHelper.getAsInt(json, "max_population", Integer.MAX_VALUE);
        boolean withinBorder = GsonHelper.getAsBoolean(json, "within_border", false);
        int minFreeBeds = GsonHelper.getAsInt(json, "min_free_beds", Integer.MIN_VALUE);
        Boolean playerFounded = json.has("player_founded") ? GsonHelper.getAsBoolean(json, "player_founded") : null;

        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel serverLevel)) return false;
            VillageManager manager = VillageManager.get(serverLevel);
            Optional<Village> villageOpt = manager.findNearestVillage(ctx.pos(), Village.MERGE_MARGIN);
            if (villageOpt.isEmpty()) return false;
            Village village = villageOpt.get();
            if (withinBorder && !village.isWithinBorder(ctx.entity())) return false;
            if (expectedId != Integer.MIN_VALUE && village.getId() != expectedId) return false;
            if (expectedVillageId != Integer.MIN_VALUE && village.getId() != expectedVillageId) return false;
            if (expectedName != null && !expectedName.equalsIgnoreCase(village.getName())) return false;
            int buildingCount = com.aetherianartificer.townstead.compat.mca.McaBuildings.allById(village).size();
            if (buildingCount < minBuildings || buildingCount > maxBuildings) return false;
            int population = village.getResidents(serverLevel).size();
            if (population < minPopulation || population > maxPopulation) return false;
            if (minFreeBeds != Integer.MIN_VALUE && village.getMaxPopulation() - population < minFreeBeds) return false;
            if (playerFounded != null) {
                var birth = com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.get(serverLevel.getServer())
                        .getVillageBirth(new com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey(
                                serverLevel.dimension().location(), village.getId()));
                if (birth == null || birth.playerFounded() != playerFounded) return false;
            }
            return true;
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
}
