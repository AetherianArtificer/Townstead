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
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Lowers v2 authoring sugar into the canonical JSON the existing parsers already understand,
 * so the ergonomic form and the explicit form compile to exactly the same nodes. Runs only when
 * a gene declares {@code "schema": "townstead:gene/v2"}; v1 resources are returned
 * untouched, so legacy data is never reinterpreted. The former {@code pheno_version: 2}
 * declaration remains accepted as a compatibility alias.
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
        String geneType = GsonHelper.getAsString(root, "type", "");
        // Each variant config is governed by the gene's own type, so normalize it under that
        // type (sugar, units, aliases, nested shorthand) just like a single-variant root.
        if (root.has("variants") && root.get("variants").isJsonObject()) {
            JsonObject variants = root.getAsJsonObject("variants");
            for (String key : new ArrayList<>(variants.keySet())) {
                if (variants.get(key).isJsonObject()) {
                    JsonObject variant = variants.get(key).getAsJsonObject();
                    normalizeNode(variant, NodeDomain.GENE, geneType);
                }
            }
        } else {
            normalizeNode(root, NodeDomain.GENE, geneType);
        }
        return root;
    }

    /** Normalize a standalone v2 condition node used by another Townstead data format. */
    public static JsonObject normalizeCondition(JsonObject condition) {
        JsonObject out = condition.deepCopy();
        normalizeNode(out, NodeDomain.CONDITION, "");
        return out;
    }

    /** Normalize a standalone v2 action node used by another Townstead data format. */
    public static JsonElement normalizeAction(JsonElement action) {
        return normalizeChild(action.deepCopy(), NodeDomain.ACTION);
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

    /** {@code { "on": "attack", "do": {...} }} -> a trigger gene; {@code { "on": { "press": "jump" } }} too. */
    private static void liftTriggerSugar(JsonObject root) {
        if (!root.has("on") || root.has("type")) return;
        JsonElement on = root.get("on");
        root.addProperty("type", "pheno:trigger");
        if (on.isJsonObject() && on.getAsJsonObject().has("press")) {
            root.addProperty("trigger", "press");
            root.addProperty("key", GsonHelper.getAsString(on.getAsJsonObject(), "press", "jump"));
        } else {
            root.addProperty("trigger", triggerName(on.getAsString()));
        }
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

    private static void normalizeNode(JsonObject obj, NodeDomain domain, String typeHint) {
        String type = obj.has("type") ? GsonHelper.getAsString(obj, "type", "") : typeHint;
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
        if (domain == NodeDomain.ACTION
                && "pheno:with".equals(GsonHelper.getAsString(obj, "type", ""))) {
            obj = lowerWith(obj);
        }
        normalizeNode(obj, domain, "");
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
        out.addProperty("type", "pheno:" + type);
        out.add(childKey, inner);
        return out;
    }

    private static JsonObject equipped(String slot, JsonElement inner) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "pheno:equipped_item_action");
        out.addProperty("slot", slot);
        out.add("item_action", inner);
        return out;
    }

    private static JsonObject blockOffset(JsonElement inner) {
        JsonObject offset = new JsonObject();
        offset.addProperty("type", "pheno:offset");
        offset.addProperty("y", -1);
        offset.add("block_action", inner);
        return wrap("block_action", "block_action", offset);
    }

    /**
     * Convert a unit string to its canonical number. A malformed value is returned unchanged
     * (never coerced to 0): the validator then reports it against the field's type, so a typo
     * like {@code "tomorrowish"} surfaces as an error rather than silently becoming no cooldown.
     */
    private static JsonPrimitive normalizeUnit(FieldSchema field, JsonPrimitive value) {
        if (value.isNumber()) return value;
        String raw = value.getAsString().trim().toLowerCase(Locale.ROOT);
        switch (field.type()) {
            case DURATION:
                OptionalInt ticks = parseDurationTicks(raw);
                return ticks.isPresent() ? new JsonPrimitive(ticks.getAsInt()) : value;
            case PERCENT:
                if (raw.endsWith("%")) {
                    OptionalDouble percent = parseDoubleOpt(raw.substring(0, raw.length() - 1));
                    return percent.isPresent() ? new JsonPrimitive(percent.getAsDouble() / 100d) : value;
                }
                return value;
            default:
                return value;
        }
    }

    private static OptionalInt parseDurationTicks(String raw) {
        String body;
        double scale;
        if (raw.endsWith("ms")) { body = raw.substring(0, raw.length() - 2); scale = 1d / 50d; }
        else if (raw.endsWith("s")) { body = raw.substring(0, raw.length() - 1); scale = 20d; }
        else if (raw.endsWith("t")) { body = raw.substring(0, raw.length() - 1); scale = 1d; }
        else { body = raw; scale = 1d; }
        OptionalDouble parsed = parseDoubleOpt(body);
        return parsed.isPresent() ? OptionalInt.of((int) Math.round(parsed.getAsDouble() * scale)) : OptionalInt.empty();
    }

    private static OptionalDouble parseDoubleOpt(String s) {
        try {
            return OptionalDouble.of(Double.parseDouble(s.trim()));
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }
}
