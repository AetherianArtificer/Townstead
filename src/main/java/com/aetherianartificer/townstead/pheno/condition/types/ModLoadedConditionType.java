package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * True when a mod is installed. With a list, when any of them is.
 * <pre>{ "type": "pheno:mod_loaded", "mod": "farmersdelight" }</pre>
 * <pre>{ "type": "pheno:mod_loaded", "mod": ["starcatcher", "aquaculture"] }</pre>
 */
public final class ModLoadedConditionType implements ConditionType {
    public static final String KEY = "pheno:mod_loaded";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        JsonElement raw = json.get("mod");
        if (raw == null) return null;
        List<String> mods = new ArrayList<>();
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> mods.add(e.getAsString()));
        else mods.add(raw.getAsString());
        if (mods.isEmpty() || mods.stream().anyMatch(String::isBlank)) return null;
        boolean loaded = mods.stream().anyMatch(ModCompat::isLoaded);
        return new Condition() {
            @Override public boolean test(ConditionContext ctx) { return loaded; }
            @Override public boolean supportsSubject() { return true; }
        };
    }
}
