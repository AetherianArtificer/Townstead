package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Adds {@code amount} to a Chronicle counter on the entity, or on the other side with
 * {@code "on": "other"} (in a story, the player). One story can leave a mark that another reads
 * with {@code pheno:chronicle_count}: a question the player is carrying, an answer they heard.
 * <pre>
 * { "type": "pheno:add_count", "key": "townstead:asked_neighbor", "on": "other" }
 * </pre>
 */
public final class AddCountActionType implements ActionType {
    public static final String KEY = "pheno:add_count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        if (key.isBlank()) return null;
        int amount = GsonHelper.getAsInt(json, "amount", 1);
        boolean other = "other".equals(GsonHelper.getAsString(json, "on", "self"));
        return ctx -> {
            LivingEntity target = other ? ctx.other() : ctx.entity();
            if (target == null || !(target.level() instanceof ServerLevel level)) {
                ctx.fail();
                return;
            }
            Chronicles.addCounter(level.getServer(), target.getUUID(), key, amount);
        };
    }
}
