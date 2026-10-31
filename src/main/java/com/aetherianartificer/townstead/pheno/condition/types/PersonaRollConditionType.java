package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.persona.PersonaInstances;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * What this world rolled for a Persona. {@code value} is one value or a list (any of);
 * {@code contains} matches one entry of a comma-separated value such as {@code "farmer,cook"}.
 * False while the Persona has not rolled it yet.
 * <pre>{ "type": "pheno:persona_roll", "persona": "townstead:village_builder", "roll": "hometown", "value": "mill" }</pre>
 * <pre>{ "type": "pheno:persona_roll", "persona": "townstead:village_builder", "roll": "townsfolk", "contains": "farmer" }</pre>
 */
public final class PersonaRollConditionType implements ConditionType {
    public static final String KEY = "pheno:persona_roll";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation persona = ResourceLocation.tryParse(GsonHelper.getAsString(json, "persona", ""));
        String roll = GsonHelper.getAsString(json, "roll", "");
        if (persona == null || roll.isBlank()) return null;
        List<String> values = new ArrayList<>();
        JsonElement raw = json.get("value");
        if (raw != null && raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> values.add(e.getAsString()));
        else if (raw != null) values.add(raw.getAsString());
        String contains = GsonHelper.getAsString(json, "contains", "");
        return new Condition() {
            @Override
            public boolean test(ConditionContext ctx) {
                if (ctx.level() == null || ctx.level().getServer() == null) return false;
                String rolled = PersonaInstances.get(ctx.level().getServer()).rolled(persona, roll);
                if (rolled == null) return false;
                if (!contains.isEmpty()) {
                    for (String part : rolled.split(",")) if (part.trim().equals(contains)) return true;
                    return false;
                }
                return values.isEmpty() || values.contains(rolled);
            }

            @Override
            public boolean supportsSubject() {
                return true;
            }
        };
    }
}
