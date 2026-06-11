package com.aetherianartificer.townstead.pheno.lang.normalize;

import com.aetherianartificer.townstead.pheno.lang.PhenoVersion;
import com.aetherianartificer.townstead.pheno.lang.schema.FieldSchema;
import com.aetherianartificer.townstead.pheno.lang.schema.NodeSchema;
import com.aetherianartificer.townstead.pheno.lang.schema.NodeSchemas;
import com.aetherianartificer.townstead.pheno.lang.validate.ChildSlots;
import com.aetherianartificer.townstead.pheno.lang.validate.NodeDomain;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lowers v2 authoring sugar into the canonical JSON the existing parsers already understand,
 * so the ergonomic form and the explicit form compile to exactly the same nodes. Runs only when
 * a resource declares {@code "pheno_version": 2}; v1 resources are returned untouched, so legacy
 * data is never reinterpreted.
 *
 * <p>It applies: the {@code pheno:} namespace shorthand; the {@code gene} envelope (genetic
 * metadata separated from behavior); {@code on}+{@code do} trigger shorthand; the {@code with}
 * context transition (lowered to the actor/target/item/block wrappers); the universal
 * {@code when}->{@code condition} and {@code do}->primary-child renames; schema field aliases;
 * and unit ({@code "3s"}, {@code "50%"}) and scalar-or-list normalization.
 */
public final class PhenoNormalizer {

    private PhenoNormalizer() {}

    /** Normalize a gene file root. Returns the input unchanged for v1; a new tree for v2. */
    public static JsonObject normalize(JsonObject geneRoot) {
        if (PhenoVersion.of(geneRoot) != PhenoVersion.V2) return geneRoot;
        JsonObject root = geneRoot.deepCopy();
        hoistGeneEnvelope(root);
        liftTriggerSugar(root);
        applyNamespaceAlias(root);
        normalizeNode(root, NodeDomain.GENE);
        return root;
    }

    /** {@code "gene": { "dominance": ..., "category": ... }} -> top-level fields. */
    private static void hoistGeneEnvelope(JsonObject root) {
        if (!root.has("gene") || !root.get("gene").isJsonObject()) return;
        JsonObject gene = root.getAsJsonObject("gene");
        for (String key : new String[]{"dominance", "category", "weight", "locus"}) {
            if (gene.has(key) && !root.has(key)) root.add(key, gene.get(key));
        }
        root.remove("gene");
    }

    /** {@code { "on": "attack", "do": {...} }} -> a trigger gene. */
    private static void liftTriggerSugar(JsonObject root) {
        if (!root.has("on") || root.has("type")) return;
        String on = GsonHelper.getAsString(root, "on", "");
        root.addProperty("type", "townstead_origins:trigger");
        root.addProperty("trigger", triggerName(on));
        if (!root.has("target")) root.addProperty("target", "self");
        if (root.has("do") && !root.has("action")) root.add("action", root.get("do"));
        root.remove("on");
        root.remove("do");
    }

    private static String triggerName(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "attack" -> "when_attack";
            case "hurt", "damaged", "harmed" -> "when_hurt";
            case "kill" -> "when_kill";
            case "death", "die" -> "when_death";
            case "land" -> "when_land";
            case "jump" -> "when_jump";
            case "wake", "wake_up" -> "when_wake_up";
            case "equip" -> "when_equip";
            case "use_item", "item_use" -> "when_item_use";
            case "lightning", "struck_by_lightning" -> "when_struck_by_lightning";
            default -> t.startsWith("when_") ? t : "when_" + t;
        };
    }

    /** Rewrite a {@code pheno:} type to the canonical {@code townstead_origins:} namespace. */
    private static void applyNamespaceAlias(JsonObject obj) {
        if (obj.has("type") && obj.get("type").isJsonPrimitive()) {
            String type = obj.get("type").getAsString();
            if (type.startsWith("pheno:")) {
                obj.addProperty("type", "townstead_origins:" + type.substring("pheno:".length()));
            }
        }
    }

    private static void normalizeNode(JsonObject obj, NodeDomain domain) {
        String type = GsonHelper.getAsString(obj, "type", "");
        NodeSchema schema = NodeSchemas.get(type);

        // Universal renames.
        if (obj.has("when") && !obj.has("condition")) {
            obj.add("condition", obj.get("when"));
            obj.remove("when");
        }
        if (obj.has("do")) {
            String primary = schema != null && schema.primaryChild() != null ? schema.primaryChild() : "action";
            if (!obj.has(primary)) {
                obj.add(primary, obj.get("do"));
                obj.remove("do");
            }
        }

        // Schema-driven field aliases, unit normalization, and scalar-or-list.
        if (schema != null) {
            for (FieldSchema field : schema.fields()) {
                if (!obj.has(field.name())) {
                    for (String alias : field.aliases()) {
                        if (obj.has(alias)) {
                            obj.add(field.name(), obj.get(alias));
                            obj.remove(alias);
                            break;
                        }
                    }
                }
                if (!obj.has(field.name())) continue;
                JsonElement value = obj.get(field.name());
                if (field.type().isUnit() && value.isJsonPrimitive()) {
                    obj.add(field.name(), normalizeUnit(field, value.getAsJsonPrimitive()));
                } else if (field.list() && !value.isJsonArray()) {
                    JsonArray arr = new JsonArray();
                    arr.add(value);
                    obj.add(field.name(), arr);
                }
            }
        }

        // Recurse into typed child slots (replacing each with its normalized form).
        List<String> keys = new ArrayList<>(obj.keySet());
        for (String key : keys) {
            NodeDomain childDomain = ChildSlots.childDomain(domain, key);
            if (childDomain == null) continue;
            obj.add(key, normalizeChild(obj.get(key), childDomain));
        }
    }

    private static JsonElement normalizeChild(JsonElement value, NodeDomain domain) {
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement element : value.getAsJsonArray()) {
                out.add(normalizeChild(element, domain));
            }
            return out;
        }
        if (!value.isJsonObject()) return value;
        JsonObject obj = value.getAsJsonObject();
        applyNamespaceAlias(obj);
        if (domain == NodeDomain.ACTION
                && "townstead_origins:with".equals(GsonHelper.getAsString(obj, "type", ""))) {
            obj = lowerWith(obj);
        }
        normalizeNode(obj, domain);
        return obj;
    }

    /** Lower a {@code with} context transition into the matching canonical wrapper. */
    private static JsonObject lowerWith(JsonObject withNode) {
        String target = GsonHelper.getAsString(withNode, "target", "self").toLowerCase(Locale.ROOT);
        JsonElement inner = withNode.has("do") ? withNode.get("do")
                : withNode.has("action") ? withNode.get("action") : null;
        if (inner == null) return withNode;
        return switch (target) {
            case "self", "actor" -> wrap("actor_action", "action", inner);
            case "target", "attacker", "victim", "other" -> wrap("target_action", "action", inner);
            case "held_item", "mainhand" -> equipped("mainhand", inner);
            case "offhand" -> equipped("offhand", inner);
            case "block_below" -> blockOffset(inner);
            case "block_here", "block_at" -> wrap("block_action", "block_action", inner);
            default -> withNode;
        };
    }

    private static JsonObject wrap(String type, String childKey, JsonElement inner) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "townstead_origins:" + type);
        out.add(childKey, inner);
        return out;
    }

    private static JsonObject equipped(String slot, JsonElement inner) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "townstead_origins:equipped_item_action");
        out.addProperty("slot", slot);
        out.add("item_action", inner);
        return out;
    }

    private static JsonObject blockOffset(JsonElement inner) {
        JsonObject offset = new JsonObject();
        offset.addProperty("type", "townstead_origins:offset");
        offset.addProperty("y", -1);
        offset.add("block_action", inner);
        return wrap("block_action", "block_action", offset);
    }

    private static JsonPrimitive normalizeUnit(FieldSchema field, JsonPrimitive value) {
        if (value.isNumber()) return value;
        String raw = value.getAsString().trim().toLowerCase(Locale.ROOT);
        switch (field.type()) {
            case DURATION:
                return new JsonPrimitive(parseDurationTicks(raw));
            case PERCENT:
                if (raw.endsWith("%")) {
                    return new JsonPrimitive(parseDouble(raw.substring(0, raw.length() - 1)) / 100d);
                }
                return value;
            default:
                return value;
        }
    }

    private static int parseDurationTicks(String raw) {
        try {
            if (raw.endsWith("ms")) return (int) Math.round(parseDouble(raw.substring(0, raw.length() - 2)) / 50d);
            if (raw.endsWith("s")) return (int) Math.round(parseDouble(raw.substring(0, raw.length() - 1)) * 20d);
            if (raw.endsWith("t")) return (int) Math.round(parseDouble(raw.substring(0, raw.length() - 1)));
            return (int) Math.round(parseDouble(raw));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s.trim());
    }
}
