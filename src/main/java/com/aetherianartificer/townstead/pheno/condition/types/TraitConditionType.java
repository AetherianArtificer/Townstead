package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.compat.mca.McaTraitCompat;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;

import java.util.ArrayList;
import java.util.List;

/**
 * True when the villager in focus has an MCA trait, such as {@code athletic} or {@code tough}, or
 * a trait a data pack adds. With a list, when it has any of them.
 * <pre>{ "type": "pheno:trait", "trait": ["athletic", "tough"] }</pre>
 */
public final class TraitConditionType implements ConditionType {
    public static final String KEY = "pheno:trait";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        JsonElement raw = json.get("trait");
        if (raw == null) return null;
        List<String> wanted = new ArrayList<>();
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> wanted.add(e.getAsString().trim()));
        else wanted.add(raw.getAsString().trim());
        if (wanted.isEmpty()) return null;
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return false;
            // Resolved at test time: data-pack traits register after conditions parse.
            for (String id : wanted) {
                Traits.Trait trait = McaTraitCompat.resolve(id).orElse(null);
                if (trait != null && villager.getTraits().hasTrait(trait)) return true;
            }
            return false;
        };
    }
}
