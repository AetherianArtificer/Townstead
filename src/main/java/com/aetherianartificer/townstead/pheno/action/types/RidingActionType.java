package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs the wrapped entity action on the living vehicle the actor is riding (Apoli's
 * {@code riding_action}); the vehicle becomes the inner action's {@code entity()}.
 *
 * <p>JSON: {@code { "type":"pheno:riding_action",
 * "action":{ "type":"pheno:damage", "amount":2 } }}</p>
 */
public final class RidingActionType implements ActionType {

    public static final String KEY = "pheno:riding_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            if (ctx.entity().getVehicle() instanceof LivingEntity vehicle) {
                inner.run(new ActionContext(vehicle, ctx.entity()));
            }
        };
    }
}
