package com.aetherianartificer.townstead.pheno.lang.validate;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.JsonPath;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

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
        // A variants block holds per-variant config objects (each governed by the gene's own
        // type); descend each in place. Otherwise the behavior tree starts at the root.
        if (root.has("variants") && root.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("variants").entrySet()) {
                if (e.getValue().isJsonObject()) {
                    descend(e.getValue().getAsJsonObject(), NodeDomain.GENE,
                            JsonPath.ROOT.field("variants").field(e.getKey()), diag);
                }
            }
        } else {
            descend(root, NodeDomain.GENE, JsonPath.ROOT, diag);
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
        descend(obj, domain, path, diag);
    }

    /** Follow the canonical typed child slots of an object of {@code parentDomain}. */
    private static void descend(JsonObject obj, NodeDomain parentDomain, JsonPath path, Diagnostics diag) {
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            NodeDomain child = childDomain(parentDomain, e.getKey());
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

    /**
     * The domain of a child slot given its parent's domain, or {@code null} when the key is not
     * a typed slot the validator descends. Only unambiguous slots are mapped.
     */
    @Nullable
    private static NodeDomain childDomain(NodeDomain parent, String key) {
        switch (parent) {
            case GENE:
            case ACTION:
                switch (key) {
                    case "action":
                    case "entity_action":
                    case "actions":
                    case "bientity_action":
                        return NodeDomain.ACTION;
                    case "condition":
                    case "conditions":
                    case "mob_condition":
                        return NodeDomain.CONDITION;
                    case "block_action":
                        return NodeDomain.BLOCK_ACTION;
                    case "item_action":
                        return NodeDomain.ITEM_ACTION;
                    case "bientity_condition":
                        return NodeDomain.BIENTITY_CONDITION;
                    default:
                        return null;
                }
            case CONDITION:
                switch (key) {
                    case "condition":
                    case "conditions":
                        return NodeDomain.CONDITION;
                    case "bientity_condition":
                        return NodeDomain.BIENTITY_CONDITION;
                    default:
                        return null;
                }
            case BLOCK_ACTION:
                return key.equals("block_action") ? NodeDomain.BLOCK_ACTION : null;
            case ITEM_ACTION:
                if (key.equals("item_action")) return NodeDomain.ITEM_ACTION;
                if (key.equals("action")) return NodeDomain.ACTION;
                return null;
            default:
                // Bi-entity subtrees mix entity and bi-entity conditions under the same key, so
                // they are left un-descended rather than risk a wrong finding.
                return null;
        }
    }
}
