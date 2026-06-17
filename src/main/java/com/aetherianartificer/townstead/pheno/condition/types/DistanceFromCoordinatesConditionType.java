package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

/**
 * True when the entity is within {@code [min, max]} blocks of {@code (x,y,z)} (Apoli's
 * {@code distance_from_coordinates}).
 *
 * <p>JSON: {@code { "type":"pheno:distance_from_coordinates", "x":0, "z":0,
 * "max":100 }}</p>
 */
public final class DistanceFromCoordinatesConditionType implements ConditionType {

    public static final String KEY = "pheno:distance_from_coordinates";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Vec3 target = new Vec3(GsonHelper.getAsDouble(json, "x", 0),
                GsonHelper.getAsDouble(json, "y", 0), GsonHelper.getAsDouble(json, "z", 0));
        double min = GsonHelper.getAsDouble(json, "min", 0);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        boolean ignoreY = GsonHelper.getAsBoolean(json, "ignore_y", false);
        return ctx -> {
            Vec3 pos = ctx.entity().position();
            double d = ignoreY
                    ? Math.sqrt(Math.pow(pos.x - target.x, 2) + Math.pow(pos.z - target.z, 2))
                    : pos.distanceTo(target);
            return d >= min && d <= max;
        };
    }
}
