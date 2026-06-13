package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Translates an Apoli bi-entity-condition JSON into the Townstead bi-entity subset
 * (see {@code pheno/condition/bientity}). The scoped metas
 * ({@code actor_condition}/{@code target_condition}/{@code both}/{@code either}) recurse
 * into {@link ApoliConditionTranslator} for their inner entity condition; the directional
 * metas ({@code invert}/{@code undirected}) recurse into this translator. Returns
 * {@code null} outside the subset so the gated power is skip-logged rather than
 * half-converted. Offline tool only.
 */
public final class ApoliBiEntityConditionTranslator {

    private ApoliBiEntityConditionTranslator() {}

    @Nullable
    public static JsonObject translate(@Nullable JsonObject apoli) {
        if (apoli == null) return null;
        String type = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(apoli, "type", ""));
        JsonObject out = base(type, apoli);
        if (out == null) return null;
        if (GsonHelper.getAsBoolean(apoli, "inverted", false)) out.addProperty("inverted", true);
        return out;
    }

    @Nullable
    private static JsonObject base(String type, JsonObject apoli) {
        switch (type) {
            // Relationship leaves (no fields)
            case "attack_target": return simple("attack_target");
            case "attacker": return simple("attacker");
            case "prime_adversary": return simple("prime_adversary");
            case "can_see": return simple("can_see");
            case "owner": return simple("owner");
            case "riding": return simple("riding");
            case "riding_root": return simple("riding_root");
            case "riding_recursive": return simple("riding_recursive");
            case "equal": return simple("equal");
            case "distance": {
                JsonObject out = simple("distance");
                String comparison = GsonHelper.getAsString(apoli, "comparison", "<=");
                double compareTo = GsonHelper.getAsDouble(apoli, "compare_to", 0d);
                if (comparison.startsWith(">")) out.addProperty("min", compareTo);
                else if (comparison.startsWith("<")) out.addProperty("max", compareTo);
                else if (comparison.startsWith("=")) { out.addProperty("min", compareTo); out.addProperty("max", compareTo); }
                return out;
            }
            // Scoped metas: inner is an ENTITY condition
            case "actor_condition": return scoped("actor_condition", apoli);
            case "target_condition": return scoped("target_condition", apoli);
            case "both": return scoped("both", apoli);
            case "either": return scoped("either", apoli);
            // Directional metas: inner is a BI-ENTITY condition
            case "invert": return directional("invert", apoli);
            case "undirected": return directional("undirected", apoli);
            // Set membership: is the target in the actor's collection (the set)
            case "in_set": {
                String set = GsonHelper.getAsString(apoli, "set", "");
                if (set.isEmpty()) return null;
                JsonObject out = simple("collection_contains");
                out.addProperty("collection", set);
                return out;
            }
            // Apugli compare_dimensions: the actor's live size against the target's, per axis
            case "compare_dimensions": {
                JsonObject out = simple("compare_dimensions");
                out.addProperty("which", ApoliConditionTranslator.whichFromSet(apoli));
                out.addProperty("comparison", GsonHelper.getAsString(apoli, "comparison", ">="));
                return out;
            }
            // Apugli compare_scales (Pehkui): the actor's scale against the target's, per axis
            case "compare_scales": {
                JsonObject out = simple("compare_scales");
                out.addProperty("which", ApoliConditionTranslator.whichFromScaleType(apoli));
                out.addProperty("comparison", GsonHelper.getAsString(apoli, "comparison", ">="));
                return out;
            }
            // Apugli hits-on-target: how many times the actor has hit the target, against the implicit counter
            case "hits_on_target": {
                JsonObject out = simple("collection_count");
                out.addProperty("collection", PowerToGeneConverter.HITS_ON_TARGET_COLLECTION);
                out.addProperty("comparison", GsonHelper.getAsString(apoli, "comparison", ">="));
                out.addProperty("compare_to", GsonHelper.getAsInt(apoli, "compare_to", 1));
                return out;
            }
            // relative_rotation has no clean mapping onto our forward-cone form, so it is
            // skip-logged rather than converted wrong.
            default: return null;
        }
    }

    @Nullable
    private static JsonObject scoped(String name, JsonObject apoli) {
        if (!apoli.has("condition") || !apoli.get("condition").isJsonObject()) return null;
        JsonObject inner = ApoliConditionTranslator.translate(apoli.getAsJsonObject("condition"));
        if (inner == null) return null;
        JsonObject out = simple(name);
        out.add("condition", inner);
        return out;
    }

    @Nullable
    private static JsonObject directional(String name, JsonObject apoli) {
        if (!apoli.has("condition") || !apoli.get("condition").isJsonObject()) return null;
        JsonObject inner = translate(apoli.getAsJsonObject("condition"));
        if (inner == null) return null;
        JsonObject out = simple(name);
        out.add("condition", inner);
        return out;
    }

    private static JsonObject simple(String name) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "pheno:" + name);
        return out;
    }
}
