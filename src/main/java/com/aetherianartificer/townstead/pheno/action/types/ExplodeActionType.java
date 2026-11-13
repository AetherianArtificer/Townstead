package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;

/**
 * Detonates an explosion at the actor (Apoli's entity {@code explode}); the actor is
 * credited as the source, or the bearer with {@code "credit":"origin"}. Same six-arg overload on both branches.
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
        // An explosion never hurts the entity it is credited to. "credit":"origin" credits the bearer,
        // so a blast set on another creature hurts that creature and spares the one who set it.
        boolean creditOrigin = "origin".equals(GsonHelper.getAsString(json, "credit", "self"));
        return ctx -> ctx.entity().level().explode(
                creditOrigin && ctx.origin() != null ? ctx.origin() : ctx.entity(),
                ctx.entity().getX(), ctx.entity().getY(), ctx.entity().getZ(), power, fire,
                destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
    }
}
