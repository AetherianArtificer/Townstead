package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.marker.PlayerMarkers;
import com.google.gson.JsonObject;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * True when the entity is within {@code radius} blocks of a place marked for a player by
 * {@code pheno:mark_structure}. For a player, their own marker; for anyone else, the marker of
 * the {@code other} player in the context (a villager travelling with you).
 * <pre>{ "type": "pheno:near_marker", "key": "old_town", "radius": 48 }</pre>
 */
public final class NearMarkerConditionType implements ConditionType {
    public static final String KEY = "pheno:near_marker";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        if (key.isBlank()) return null;
        int radius = Math.max(1, GsonHelper.getAsInt(json, "radius", 48));
        return ctx -> {
            LivingEntity entity = ctx.entity();
            if (entity == null) return false;
            Player owner = entity instanceof Player p ? p : ctx.other() instanceof Player p ? p : null;
            if (owner == null) return false;
            GlobalPos marker = PlayerMarkers.get(owner, key);
            return marker != null && marker.dimension() == entity.level().dimension()
                    && marker.pos().distSqr(entity.blockPosition()) <= (double) radius * radius;
        };
    }
}
