package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * Passes randomly with probability {@code chance} (Apoli's {@code chance}).
 * Rolled per evaluation against the entity's RNG, so a gated effect flickers
 * on at that rate. {@code chance} is clamped to {@code [0, 1]}.
 *
 * <p>JSON: {@code { "type":"pheno:chance", "chance":0.25 }}</p>
 */
public final class ChanceConditionType implements ConditionType {

    public static final String KEY = "pheno:chance";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        float chance = Mth.clamp(GsonHelper.getAsFloat(json, "chance", 0.5f), 0.0f, 1.0f);
        return ctx -> ctx.entity().getRandom().nextFloat() < chance;
    }
}
