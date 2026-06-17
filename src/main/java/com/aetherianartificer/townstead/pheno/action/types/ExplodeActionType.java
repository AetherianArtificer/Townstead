package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;

/**
 * Detonates an explosion at the actor (Apoli's entity {@code explode}); the actor is
 * credited as the source. Same six-arg overload on both branches.
 *
 * <p>JSON: {@code { "type":"pheno:explode", "power":3.0, "fire":false,
 * "destroy":true }}</p>
 */
public final class ExplodeActionType implements ActionType {

    public static final String KEY = "pheno:explode";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float power = GsonHelper.getAsFloat(json, "power", 2.0f);
        boolean fire = GsonHelper.getAsBoolean(json, "fire", false);
        boolean destroy = GsonHelper.getAsBoolean(json, "destroy", true);
        return ctx -> ctx.entity().level().explode(ctx.entity(),
                ctx.entity().getX(), ctx.entity().getY(), ctx.entity().getZ(), power, fire,
                destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
    }
}
