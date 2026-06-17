package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

/**
 * Compares the number of living entities within {@code radius} (matching an optional
 * entity {@code condition}) against {@code compare_to} using {@code comparison}
 * (Apugli's {@code entity_in_radius}). The bearer itself is excluded.
 *
 * <p>JSON: {@code { "type":"pheno:entity_in_radius", "radius":8,
 * "comparison":">=", "compare_to":3,
 * "condition":{ "type":"pheno:entity_type", "entity_type":"minecraft:zombie" } }}</p>
 */
public final class EntityInRadiusConditionType implements ConditionType {

    public static final String KEY = "pheno:entity_in_radius";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        double radius = GsonHelper.getAsDouble(json, "radius", 8.0);
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 1);
        @Nullable Condition inner = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        if (json.has("condition") && inner == null) return null;
        return ctx -> {
            LivingEntity self = ctx.entity();
            var nearby = self.level().getEntitiesOfClass(LivingEntity.class,
                    self.getBoundingBox().inflate(radius));
            int count = 0;
            for (LivingEntity other : nearby) {
                if (other == self) continue;
                if (inner != null && !inner.test(new ConditionContext(other))) continue;
                count++;
            }
            return comparison.compare(count, compareTo);
        };
    }
}
