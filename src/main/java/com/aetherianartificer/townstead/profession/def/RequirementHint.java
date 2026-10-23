package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A display-side summary of one leaf inside a def's {@code requirements} condition, extracted
 * at parse time so career surfaces can render live progress ("Cooked dishes 18/25") without
 * making the condition system introspectable. Evaluation still belongs solely to the parsed
 * pheno {@code Condition}; hints are presentation, never authority. Unknown condition types
 * surface as {@link #KIND_OTHER} so packs with custom gates still show an honest "special
 * condition" line instead of nothing.
 */
public record RequirementHint(String kind, String key, int target) {

    public static final String KIND_CHRONICLE_COUNT = "chronicle_count";
    public static final String KIND_CAREER_XP = "career_xp";
    public static final String KIND_OTHER = "other";

    /** Recursively collects hints from a requirements JSON tree (and/or/not composites). */
    public static List<RequirementHint> extract(JsonElement requirements) {
        List<RequirementHint> out = new ArrayList<>();
        collect(requirements, out);
        return List.copyOf(out);
    }

    private static void collect(JsonElement element, List<RequirementHint> out) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject json = element.getAsJsonObject();
        String type = GsonHelper.getAsString(json, "type", "");
        switch (type) {
            case "pheno:and", "pheno:or" -> {
                for (JsonElement child : GsonHelper.getAsJsonArray(json, "conditions", new JsonArray())) {
                    collect(child, out);
                }
            }
            case "pheno:not" -> collect(json.get("condition"), out);
            case "pheno:chronicle_count" -> out.add(new RequirementHint(KIND_CHRONICLE_COUNT,
                    GsonHelper.getAsString(json, "key", ""),
                    GsonHelper.getAsInt(json, "at_least", 1)));
            case "pheno:career_xp" -> out.add(new RequirementHint(KIND_CAREER_XP,
                    GsonHelper.getAsString(json, "career", ""),
                    GsonHelper.getAsInt(json, "at_least", 1)));
            default -> {
                if (!type.isBlank()) out.add(new RequirementHint(KIND_OTHER, type, 0));
            }
        }
    }
}
