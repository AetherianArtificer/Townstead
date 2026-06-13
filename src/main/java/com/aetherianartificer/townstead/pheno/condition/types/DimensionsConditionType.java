package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * True when the entity's live bounding-box size is within {@code [min,max]} (Apugli's
 * {@code dimensions}, the physical size for the current pose, not the world dimension). {@code which}
 * picks the axis: {@code width}, {@code height}, or {@code both} (default; both must be in range).
 *
 * <p>JSON: {@code { "type":"pheno:dimensions", "which":"height", "min":2.0 }}</p>
 */
public final class DimensionsConditionType implements ConditionType {

    public static final String KEY = "pheno:dimensions";

    public enum Which { WIDTH, HEIGHT, BOTH }

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
            if (which != Which.HEIGHT && !(e.getBbWidth() >= min && e.getBbWidth() <= max)) return false;
            if (which != Which.WIDTH && !(e.getBbHeight() >= min && e.getBbHeight() <= max)) return false;
            return true;
        };
    }

    public static Which parseWhich(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "width" -> Which.WIDTH;
            case "height" -> Which.HEIGHT;
            default -> Which.BOTH;
        };
    }
}
