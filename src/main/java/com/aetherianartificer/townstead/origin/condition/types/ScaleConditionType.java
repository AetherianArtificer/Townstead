package com.aetherianartificer.townstead.origin.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.DimensionsConditionType.Which;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import static com.aetherianartificer.townstead.pheno.condition.types.DimensionsConditionType.parseWhich;

/**
 * True when the entity's scale is within {@code [min,max]} (Apugli's Pehkui {@code scale}, reread here
 * from MCA's villager scale factors, which the body_metric/proportions genes drive). {@code which}
 * picks the axis ({@code width}=horizontal, {@code height}=vertical, or {@code both}, default).
 * Non-villager entities have no scaling here, so they read 1.0.
 *
 * <p>JSON: {@code { "type":"pheno:scale", "which":"height", "min":1.5 }}</p>
 */
public final class ScaleConditionType implements ConditionType {

    public static final String KEY = "pheno:scale";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Which which = parseWhich(GsonHelper.getAsString(json, "which", "both"));
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
        return ctx -> {
            LivingEntity e = ctx.entity();
            if (which != Which.HEIGHT && !inRange(horizontal(e), min, max)) return false;
            if (which != Which.WIDTH && !inRange(vertical(e), min, max)) return false;
            return true;
        };
    }

    public static float horizontal(LivingEntity entity) {
        return entity instanceof VillagerLike villager ? villager.getHorizontalScaleFactor() : 1.0f;
    }

    public static float vertical(LivingEntity entity) {
        if (!(entity instanceof VillagerLike villager)) return 1.0f;
        // 1.20.1 MCA has no separate vertical scale; fall back to the (uniform) horizontal factor.
        //? if >=1.21 {
        return villager.getVerticalScaleFactor();
        //?} else {
        /*return villager.getHorizontalScaleFactor();
        *///?}
    }

    private static boolean inRange(float value, float min, float max) {
        return value >= min && value <= max;
    }
}
