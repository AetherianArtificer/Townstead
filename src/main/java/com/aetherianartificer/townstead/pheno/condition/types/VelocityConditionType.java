package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * True when a component of the entity's velocity is within {@code [min, max]}
 * (Apugli's {@code velocity}). {@code axis} is {@code x}, {@code y}, {@code z},
 * {@code horizontal} (xz magnitude), or {@code total} (full magnitude, default).
 *
 * <p>JSON: {@code { "type":"pheno:velocity", "axis":"y", "max":-0.5 }}
 * (falling fast).</p>
 */
public final class VelocityConditionType implements ConditionType {

    public static final String KEY = "pheno:velocity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String axis = GsonHelper.getAsString(json, "axis", "total").toLowerCase(Locale.ROOT);
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        return ctx -> {
            Vec3 v = ctx.entity().getDeltaMovement();
            double value = switch (axis) {
                case "x" -> v.x;
                case "y" -> v.y;
                case "z" -> v.z;
                case "horizontal" -> Math.sqrt(v.x * v.x + v.z * v.z);
                default -> v.length();
            };
            return value >= min && value <= max;
        };
    }
}
