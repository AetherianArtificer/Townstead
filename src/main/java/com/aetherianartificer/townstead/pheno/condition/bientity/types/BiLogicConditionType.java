package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic over bi-entity conditions: {@code and} and {@code or} over a {@code conditions} list, and
 * {@code not} over one {@code condition}. The bi-entity twin of the entity logic conditions, so a
 * breath cone can require "in front of me, in sight, and not my ally" in one gate.
 *
 * <p>JSON: {@code { "type":"pheno:and", "conditions":[ { "type":"pheno:relative_rotation",
 * "max_angle":22 }, { "type":"pheno:can_see" } ] }}</p>
 */
public final class BiLogicConditionType implements BiEntityConditionType {

    public enum Mode { AND, OR, NOT }

    private final String key;
    private final Mode mode;

    public BiLogicConditionType(String key, Mode mode) {
        this.key = key;
        this.mode = mode;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        if (mode == Mode.NOT) {
            BiEntityCondition inner = BiEntityConditions.parse(json.get("condition"));
            return inner == null ? null : inner.negate();
        }
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) return null;
        List<BiEntityCondition> parts = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("conditions")) {
            BiEntityCondition part = BiEntityConditions.parse(element);
            if (part == null) return null;
            parts.add(part);
        }
        return mode == Mode.AND
                ? (actor, target) -> parts.stream().allMatch(c -> c.test(actor, target))
                : (actor, target) -> parts.stream().anyMatch(c -> c.test(actor, target));
    }
}
