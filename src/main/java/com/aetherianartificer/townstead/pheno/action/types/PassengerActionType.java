package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs the wrapped entity action on each living passenger riding the actor (Apoli's
 * {@code passenger_action}); the passenger becomes the inner action's {@code entity()}.
 *
 * <p>JSON: {@code { "type":"pheno:passenger_action",
 * "action":{ "type":"pheno:dismount" } }}</p>
 */
public final class PassengerActionType implements ActionType {

    public static final String KEY = "pheno:passenger_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            for (Entity passenger : ctx.entity().getPassengers()) {
                if (passenger instanceof LivingEntity living) inner.run(new ActionContext(living, ctx.entity()));
            }
        };
    }
}
