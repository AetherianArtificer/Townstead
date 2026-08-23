package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Applies shared Career progression while keeping path levels a separate authoring concept. */
public final class ProfessionProgressionOverlay {

    public static final String SCHEMA = "townstead:profession_progression/v1";

    private ProfessionProgressionOverlay() {
    }

    /** Maps cumulative public rank thresholds onto the established in-memory level spans. */
    public static void apply(JsonObject profession, JsonObject progression) {
        TownsteadSchema.validateRequired(progression, SCHEMA);
        if (!progression.has("ranks") || !progression.get("ranks").isJsonArray()) {
            throw new IllegalArgumentException("'ranks' must be an array");
        }
        if (progression.has("max_xp")) {
            throw new IllegalArgumentException("'max_xp' is not part of profession_progression/v1");
        }
        JsonArray authored = progression.getAsJsonArray("ranks");
        if (authored.isEmpty()) throw new IllegalArgumentException("'ranks' must not be empty");

        int[] thresholds = new int[authored.size()];
        JsonObject[] ranks = new JsonObject[authored.size()];
        for (int i = 0; i < authored.size(); i++) {
            JsonElement entry = authored.get(i);
            JsonObject rank = new JsonObject();
            int threshold;
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber()) {
                threshold = entry.getAsInt();
            } else if (entry.isJsonObject()) {
                rank = entry.getAsJsonObject().deepCopy();
                if (!rank.has("at") || !rank.get("at").isJsonPrimitive()
                        || !rank.getAsJsonPrimitive("at").isNumber()) {
                    throw new IllegalArgumentException("Expanded rank " + (i + 1)
                            + " requires numeric 'at'");
                }
                if (rank.has("xp") || rank.has("trades")) {
                    throw new IllegalArgumentException("Rank " + (i + 1)
                            + " uses cumulative 'at'; merchant trades belong in trade/*.json");
                }
                threshold = rank.remove("at").getAsInt();
            } else {
                throw new IllegalArgumentException("Rank " + (i + 1)
                        + " must be a cumulative XP number or an object with 'at'");
            }
            if (threshold < 0 || (i == 0 && threshold != 0)
                    || (i > 0 && threshold <= thresholds[i - 1])) {
                throw new IllegalArgumentException("Rank thresholds must start at 0 and increase");
            }
            thresholds[i] = threshold;
            ranks[i] = rank;
        }

        JsonArray levels = new JsonArray();
        for (int i = 0; i < ranks.length; i++) {
            if (i + 1 < ranks.length) ranks[i].addProperty("xp", thresholds[i + 1] - thresholds[i]);
            levels.add(ranks[i]);
        }
        profession.add("levels", levels);
        profession.addProperty("max_xp", thresholds[thresholds.length - 1]);
        if (progression.has("daily_cap")) {
            profession.add("daily_cap", progression.get("daily_cap").deepCopy());
        }
    }
}
