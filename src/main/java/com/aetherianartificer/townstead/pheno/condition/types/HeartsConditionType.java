package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

/**
 * True when the entity, an MCA villager, has between {@code min} and {@code max} hearts with the
 * other entity, a player.
 * <pre>
 *   { "type": "pheno:hearts", "min": 50 }
 * </pre>
 */
public final class HeartsConditionType implements com.aetherianartificer.townstead.pheno.condition.ConditionType {
    public static final String KEY = "pheno:hearts";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        int min = GsonHelper.getAsInt(json, "min", Integer.MIN_VALUE);
        int max = GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerLike<?> villager) || !(ctx.other() instanceof Player player)) return false;
            int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            return hearts >= min && hearts <= max;
        };
    }
}
