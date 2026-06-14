package com.aetherianartificer.townstead.pheno.lang.validate;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.JsonPath;
import com.aetherianartificer.townstead.pheno.lang.schema.FieldSchema;
import com.aetherianartificer.townstead.pheno.lang.schema.NodeSchema;
import com.aetherianartificer.townstead.pheno.lang.schema.NodeSchemas;
import com.aetherianartificer.townstead.pheno.lang.schema.PhenoType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Map;

/**
 * Walks a gene resource's behavior tree and reports any {@code "type"} that does not resolve
 * in the registry for the slot it sits in (an action under {@code action}, a condition under
 * {@code condition}, and so on). This replaces the old silent skip: an unknown type used to
 * make the whole gene vanish with at most a one-line warning, now it is a located
 * {@link com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic} with an exact JSON
 * path and a suggestion.
 *
 * <p>Recursion is deliberately conservative. It only descends through the canonical typed
 * child slots whose domain is unambiguous, so a legacy pack that compiles today produces zero
 * diagnostics. Slots with a context-dependent meaning (a block/damage condition, a bi-entity
 * subtree) have their own type validated but are not descended, so the validator never emits
 * a wrong finding.
 */
public final class PhenoValidator {

    private PhenoValidator() {}

    /** Validate a whole gene file root (its {@code type} is a gene type). */
    public static void validateGene(ResourceLocation resource, JsonObject root, Diagnostics diag) {
        diag.forResource(resource);
        String type = GsonHelper.getAsString(root, "type", "");
        if (!type.isEmpty() && !NodeDomain.GENE.resolves(type)) {
            diag.error(JsonPath.ROOT.field("type"),
                    "Unknown gene type '" + type + "'.",
                    "Check the type id and that the providing mod is loaded.");
        }
        validateResources(root, diag);
        // A variants block holds per-variant config objects (each governed by the gene's own
        // type); descend each in place. Otherwise the behavior tree starts at the root and the
        // gene's required fields are checked against its schema.
        if (root.has("variants") && root.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("variants").entrySet()) {
                if (e.getValue().isJsonObject()) {
                    JsonObject variant = e.getValue().getAsJsonObject();
                    JsonPath path = JsonPath.ROOT.field("variants").field(e.getKey());
                    // Each variant config is parsed under the gene's own type, so check its
                    // fields and descend it against that type.
                    checkFields(variant, type, path, diag);
                    descend(variant, NodeDomain.GENE, path, diag);
                }
            }
        } else {
            checkFields(root, type, JsonPath.ROOT, diag);
            descend(root, NodeDomain.GENE, JsonPath.ROOT, diag);
        }
    }

    /**
     * Validate a gene's root {@code resources} section: an object of named meters, each validated
     * as a {@code pheno:resource} node (the type defaults to that when omitted, matching how the
     * loader peels the block into companion genes).
     */
    private static void validateResources(JsonObject root, Diagnostics diag) {
        if (!root.has("resources")) return;
        JsonPath base = JsonPath.ROOT.field("resources");
        JsonElement block = root.get("resources");
        if (!block.isJsonObject()) {
            diag.error(base, "Expected an object of named resource meters.",
                    "Use { \"name\": { \"max\": 100, ... } }.");
            return;
        }
        for (Map.Entry<String, JsonElement> e : block.getAsJsonObject().entrySet()) {
            JsonPath path = base.field(e.getKey());
            if (!e.getValue().isJsonObject()) {
                diag.error(path, "Expected a resource object.", "Use { \"max\": 100, ... }.");
                continue;
            }
            JsonObject entry = e.getValue().getAsJsonObject();
            String type = entry.has("type") ? GsonHelper.getAsString(entry, "type", "") : "pheno:resource";
            if (!type.isEmpty() && !NodeDomain.GENE.resolves(type)) {
                diag.error(path.field("type"), "Unknown gene type '" + type + "'.",
                        "Check the type id and that the providing mod is loaded.");
            }
            checkFields(entry, type, path, diag);
            descend(entry, NodeDomain.GENE, path, diag);
        }
    }

    /** Validate an object expected to carry a {@code type} of the given domain, then recurse. */
    private static void validateTyped(JsonObject obj, NodeDomain domain, JsonPath path, Diagnostics diag) {
        String type = GsonHelper.getAsString(obj, "type", "");
        if (!type.isEmpty() && !domain.resolves(type)) {
            diag.error(path.field("type"),
                    "Unknown " + domain.label() + " type '" + type + "'.",
                    "Check the type id and that the providing mod is loaded.");
        }
        checkFields(obj, type, path, diag);
        descend(obj, domain, path, diag);
    }

    /**
     * Check an object's fields against its registered {@link NodeSchema}: required fields are
     * present, and present scalar fields match their declared kind, unit, list-ness, and id
     * validity. Undeclared fields are not flagged (schemas are intentionally partial), so this
     * adds real checks without false positives on legacy content. Child-node fields are
     * validated by recursion, not here.
     */
    private static void checkFields(JsonObject obj, String type, JsonPath path, Diagnostics diag) {
        NodeSchema schema = NodeSchemas.get(type);
        if (schema != null) checkFields(obj, schema, path, diag);
    }

    /**
     * Validate a plain data record's fields against a schema (attachments and other non-behavior
     * files), reusing the same required/type/list checks the gene validator uses. No behavior-tree
     * descent, so it is safe for files that carry no {@code pheno:} nodes.
     */
    public static void validateData(ResourceLocation resource, JsonObject obj, NodeSchema schema, Diagnostics diag) {
        diag.forResource(resource);
        checkFields(obj, schema, JsonPath.ROOT, diag);
    }

    private static void checkFields(JsonObject obj, NodeSchema schema, JsonPath path, Diagnostics diag) {
        for (FieldSchema field : schema.fields()) {
            boolean present = obj.has(field.name());
            if (field.required() && !present) {
                diag.error(path.field(field.name()),
                        "Missing required field '" + field.name() + "'.",
                        "Add it (" + field.type().name().toLowerCase() + ").");
                continue;
            }
            if (!present || field.type().isChild()) continue;
            JsonElement value = obj.get(field.name());
            if (field.list()) {
                if (!value.isJsonArray()) {
                    diag.error(path.field(field.name()), "Expected a list for '" + field.name() + "'.",
                            "Wrap the value in an array.");
                    continue;
                }
                JsonArray arr = value.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    checkScalar(arr.get(i), field.type(), path.field(field.name()).index(i), diag);
                }
            } else {
                checkScalar(value, field.type(), path.field(field.name()), diag);
            }
        }
    }

    private static void checkScalar(JsonElement value, PhenoType type, JsonPath path, Diagnostics diag) {
        switch (type) {
            case BOOL:
                if (!isBool(value)) diag.error(path, "Expected a boolean.", "Use true or false.");
                break;
            case INT:
            case FLOAT:
            case DISTANCE:
            case ANGLE:
                if (!isNumber(value)) diag.error(path, "Expected a number.", "Use a numeric value.");
                break;
            case DURATION:
            case PERCENT:
                if (!isNumber(value)) {
                    String shown = isString(value) ? " '" + value.getAsString() + "'" : "";
                    diag.error(path, "Malformed " + type.name().toLowerCase() + shown + ".",
                            type == PhenoType.DURATION ? "Use a tick count or e.g. \"3s\"." : "Use a fraction or e.g. \"50%\".");
                }
                break;
            case STRING:
            case COLOR:
                if (!isString(value)) diag.error(path, "Expected a string.", "Use a string value.");
                break;
            case ID:
                if (!isString(value)) diag.error(path, "Expected a resource id string.", "e.g. minecraft:stone.");
                else if (ResourceLocation.tryParse(value.getAsString()) == null) {
                    diag.error(path, "Not a valid resource id: '" + value.getAsString() + "'.", "Use namespace:path.");
                }
                break;
            case TAG_OR_ID:
                if (!isString(value)) {
                    diag.error(path, "Expected a tag or resource id.", "e.g. minecraft:stone or #minecraft:logs.");
                } else {
                    String raw = value.getAsString();
                    String body = raw.startsWith("#") ? raw.substring(1) : raw;
                    if (ResourceLocation.tryParse(body) == null) {
                        diag.error(path, "Not a valid tag/id: '" + raw + "'.", "Use namespace:path or #namespace:path.");
                    }
                }
                break;
            case OBJECT:
                if (!value.isJsonObject()) diag.error(path, "Expected an object.", null);
                break;
            default:
                break;
        }
    }

    private static boolean isBool(JsonElement v) {
        return v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isNumber(JsonElement v) {
        return v.isJsonPrimitive() && ((JsonPrimitive) v).isNumber();
    }

    private static boolean isString(JsonElement v) {
        return v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
    }

    /** Follow the canonical typed child slots of an object of {@code parentDomain}. */
    private static void descend(JsonObject obj, NodeDomain parentDomain, JsonPath path, Diagnostics diag) {
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            NodeDomain child = ChildSlots.childDomain(parentDomain, e.getKey());
            if (child == null) continue;
            visit(e.getValue(), child, path.field(e.getKey()), diag);
        }
    }

    /** Visit a value that may be a single typed object or an array of them. */
    private static void visit(JsonElement value, NodeDomain domain, JsonPath path, Diagnostics diag) {
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).isJsonObject()) {
                    validateTyped(arr.get(i).getAsJsonObject(), domain, path.index(i), diag);
                }
            }
        } else if (value.isJsonObject()) {
            validateTyped(value.getAsJsonObject(), domain, path, diag);
        }
    }

}
