package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pushes the actor's frozen ticks up by {@code amount}, capped just past the freeze
 * threshold so a repeated application (via {@code action_over_time}) keeps the entity
 * fully frozen and taking freeze damage. {@code setTicksFrozen} is identical on both
 * branches.
 *
 * <p>JSON: {@code { "type":"pheno:freeze", "amount":5 }}</p>
 */
public final class FreezeActionType implements ActionType {

    public static final String KEY = "pheno:freeze";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int amount = Math.max(1, GsonHelper.getAsInt(json, "amount", 5));
        return ctx -> {
            LivingEntity entity = ctx.entity();
            int cap = entity.getTicksRequiredToFreeze() + 20;
            entity.setTicksFrozen(Math.min(entity.getTicksFrozen() + amount, cap));
        };
    }
}
