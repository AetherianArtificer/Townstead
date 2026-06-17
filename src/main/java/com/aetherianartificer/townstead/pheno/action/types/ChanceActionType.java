package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Runs the wrapped action with probability {@code chance} (0-1; Apoli's meta
 * {@code chance}).
 *
 * <p>JSON: {@code { "type":"pheno:chance", "chance":0.25,
 * "action":{ "type":"pheno:spawn_particles", "particle":"minecraft:crit" } }}</p>
 */
public final class ChanceActionType implements ActionType {

    public static final String KEY = "pheno:chance";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float chance = GsonHelper.getAsFloat(json, "chance", 1f);
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            if (ctx.entity().getRandom().nextFloat() < chance) inner.run(ctx);
        };
    }
}
