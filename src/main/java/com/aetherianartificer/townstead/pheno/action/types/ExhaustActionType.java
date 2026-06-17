package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Adds hunger exhaustion to the actor (Apoli {@code exhaust}). Player-only: villagers
 * track hunger through Townstead's needs model rather than vanilla {@code FoodData},
 * so the action is a no-op on them. {@code addExhaustion} is identical on both
 * branches (4.0 exhaustion drains one food point).
 *
 * <p>JSON: {@code { "type":"pheno:exhaust", "amount":0.5 }}</p>
 */
public final class ExhaustActionType implements ActionType {

    public static final String KEY = "pheno:exhaust";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float amount = Math.max(0f, GsonHelper.getAsFloat(json, "amount", 0.5f));
        return ctx -> {
            if (ctx.entity() instanceof Player player) {
                player.getFoodData().addExhaustion(amount);
            }
        };
    }
}
