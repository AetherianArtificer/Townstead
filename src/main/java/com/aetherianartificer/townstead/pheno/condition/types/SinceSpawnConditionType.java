package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Compares the ticks elapsed since the entity was first tracked (~ its spawn) against {@code value},
 * giving data a persistent, one-time "time since spawn" gate without per-gene state. The first tick
 * this condition is evaluated server-side it stamps the entity's saved data with the current game
 * time; thereafter the age is {@code gameTime - stamp}, which survives relog and chunk reload (so the
 * window fires exactly once in the entity's life, not per session). The stamp is shared by all
 * {@code since_spawn} gates, so they measure from one common spawn anchor.
 *
 * <p>JSON: {@code { "type":"pheno:since_spawn", "comparison":">=", "value":1200 }} ({@code value} is
 * in ticks). Default comparison {@code >=}, default value {@code 0}.</p>
 */
public final class SinceSpawnConditionType implements ConditionType {

    public static final String KEY = "pheno:since_spawn";
    private static final String FIRST_SEEN = "townstead:first_seen";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int value = GsonHelper.getAsInt(json, "value", 0);
        return ctx -> {
            LivingEntity entity = ctx.entity();
            long now = entity.level().getGameTime();
            CompoundTag data = entity.getPersistentData();
            long firstSeen;
            if (data.contains(FIRST_SEEN)) {
                firstSeen = data.getLong(FIRST_SEEN);
            } else {
                firstSeen = now;
                if (!entity.level().isClientSide) data.putLong(FIRST_SEEN, now);
            }
            return comparison.compare(now - firstSeen, value);
        };
    }
}
