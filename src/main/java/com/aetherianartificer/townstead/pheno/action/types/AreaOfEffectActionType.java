package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs the wrapped entity action on every living entity within {@code radius} of the
 * actor (Apoli's entity {@code area_of_effect}). The actor is excluded unless
 * {@code include_self} is set; each target becomes the inner action's {@code entity()}
 * with the actor as {@code other()}. A one-shot nova (pair with a trigger), distinct
 * from the periodic {@code aura} gene.
 *
 * <p>JSON: {@code { "type":"pheno:area_of_effect", "radius":4,
 * "action":{ "type":"pheno:damage", "amount":4 } }}</p>
 */
public final class AreaOfEffectActionType implements ActionType {

    public static final String KEY = "pheno:area_of_effect";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        double radius = Math.max(0, GsonHelper.getAsDouble(json, "radius", 4));
        boolean includeSelf = GsonHelper.getAsBoolean(json, "include_self", false);
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            LivingEntity self = ctx.entity();
            for (LivingEntity target : self.level().getEntitiesOfClass(LivingEntity.class,
                    self.getBoundingBox().inflate(radius))) {
                if (target == self && !includeSelf) continue;
                inner.run(new ActionContext(target, self));
            }
        };
    }
}
