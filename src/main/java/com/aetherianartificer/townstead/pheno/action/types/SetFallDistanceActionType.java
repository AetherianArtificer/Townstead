package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Sets the actor's fall distance (Apoli's {@code set_fall_distance}); {@code 0} cancels
 * pending fall damage, useful in a {@code when_land} or movement trigger.
 *
 * <p>JSON: {@code { "type":"pheno:set_fall_distance", "fall_distance":0 }}</p>
 */
public final class SetFallDistanceActionType implements ActionType {

    public static final String KEY = "pheno:set_fall_distance";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float distance = GsonHelper.getAsFloat(json, "fall_distance", 0f);
        return ctx -> ctx.entity().fallDistance = distance;
    }
}
