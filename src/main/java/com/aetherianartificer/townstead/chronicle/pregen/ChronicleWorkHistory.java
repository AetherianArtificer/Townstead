package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estimated work a profession accumulated before Chronicle began recording it,
 * in {@code data/<ns>/chronicle_work_history/}.
 *
 * <p>A background is not a replay. Twenty years of cooking is a number, not
 * eight thousand fabricated events: these rates give a villager the counters
 * their age and trade imply, so a pregenerated cook reads as a cook to
 * {@code pheno:chronicle_count} and to Careers instead of as a newborn. The
 * archive still holds only events that really happened; this says what came
 * before the record starts.</p>
 */
public record ChronicleWorkHistory(ResourceLocation id, String profession,
                                   Map<String, Integer> perYear) {

    public static final String SCHEMA = "townstead:chronicle_work_history/v1";

    public ChronicleWorkHistory {
        profession = profession == null ? "" : profession;
        perYear = perYear == null ? Map.of() : Map.copyOf(perYear);
    }

    public static ChronicleWorkHistory parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validate(json, SCHEMA);
        Map<String, Integer> rates = new LinkedHashMap<>();
        if (json.has("per_year")) {
            JsonObject perYear = GsonHelper.getAsJsonObject(json, "per_year");
            for (String key : perYear.keySet()) {
                int rate = GsonHelper.getAsInt(perYear, key, 0);
                if (rate > 0) rates.put(key, rate);
            }
        }
        return new ChronicleWorkHistory(
                id, GsonHelper.getAsString(json, "profession", ""), rates);
    }
}
