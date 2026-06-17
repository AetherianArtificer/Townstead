package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs exactly one of {@code actions}, picked by weight (Apoli's meta {@code choice}).
 * Each entry is {@code { "action":{...}, "weight":N }} ({@code weight} defaults to 1).
 *
 * <p>JSON: {@code { "type":"pheno:choice", "actions":[
 * { "action":{...}, "weight":3 }, { "action":{...} } ] }}</p>
 */
public final class ChoiceActionType implements ActionType {

    public static final String KEY = "pheno:choice";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) return null;
        List<Action> actions = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (var element : json.getAsJsonArray("actions")) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            Action action = Actions.parse(entry.get("action"));
            if (action == null) continue;
            int weight = Math.max(1, GsonHelper.getAsInt(entry, "weight", 1));
            actions.add(action);
            weights.add(weight);
            total += weight;
        }
        if (actions.isEmpty()) return null;
        int totalWeight = total;
        return ctx -> {
            int roll = ctx.entity().getRandom().nextInt(totalWeight);
            for (int i = 0; i < actions.size(); i++) {
                roll -= weights.get(i);
                if (roll < 0) {
                    actions.get(i).run(ctx);
                    return;
                }
            }
        };
    }
}
