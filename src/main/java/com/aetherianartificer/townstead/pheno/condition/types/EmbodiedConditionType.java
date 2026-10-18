package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.Embodiment;
import com.google.gson.JsonObject;

/**
 * {@code pheno:embodied}: the bearer wears its Root's body. Always true for a villager; for a player
 * only in MCA's Villager model, since the Player and Vanilla models draw a plain player.
 *
 * <p>JSON: {@code { "type":"pheno:embodied" }}</p>
 */
public final class EmbodiedConditionType implements ConditionType {
    public static final String KEY = "pheno:embodied";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        return ctx -> ctx.entity() != null && Embodiment.embodied(ctx.entity());
    }
}
