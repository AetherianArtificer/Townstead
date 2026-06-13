package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Compares the number of passengers riding the entity, recursively (a passenger of a passenger
 * counts), against {@code compare_to} using {@code comparison} (Apoli's {@code passenger_recursive}).
 * An optional {@code where} bi-entity condition (holder, passenger) filters which passengers count.
 *
 * <p>JSON: {@code { "type":"pheno:passenger_recursive", "comparison":">=", "compare_to":1 }}</p>
 */
public final class PassengerRecursiveConditionType implements ConditionType {

    public static final String KEY = "pheno:passenger_recursive";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 1);
        BiEntityCondition where = json.has("where") ? BiEntityConditions.parse(json.get("where")) : null;
        return ctx -> comparison.compare(count(ctx.entity(), ctx.entity(), where), compareTo);
    }

    private static int count(LivingEntity root, Entity vehicle, @Nullable BiEntityCondition where) {
        int total = 0;
        for (Entity passenger : vehicle.getPassengers()) {
            if (where == null || (passenger instanceof LivingEntity living && where.test(root, living))) total++;
            total += count(root, passenger, where);
        }
        return total;
    }
}
