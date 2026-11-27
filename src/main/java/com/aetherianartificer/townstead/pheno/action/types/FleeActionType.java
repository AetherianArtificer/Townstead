package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.combat.Fears;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Makes the focus (a mob) flee for {@code duration} ticks: it drops its target and walks away from
 * the counterpart, or from the bearer when there is no counterpart. {@code speed} is the walk speed
 * factor (default 1.3); with speed 0 the mob only drops its target and stands still. Players and
 * non-mobs are not affected.
 *
 * <p>JSON: {@code { "type":"pheno:flee", "duration":100 }}</p>
 */
public final class FleeActionType implements ActionType {

    public static final String KEY = "pheno:flee";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 100));
        double speed = GsonHelper.getAsDouble(json, "speed", 1.3);
        return ctx -> {
            if (!(ctx.entity() instanceof Mob mob)) return;
            LivingEntity source = ctx.other() != null ? ctx.other() : ctx.origin();
            if (source == null || source == mob) return;
            Fears.scare(mob, source, duration, speed);
        };
    }
}
