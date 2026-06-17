package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Grants the actor experience (player-only). {@code points} adds raw XP; {@code levels}
 * adds whole levels. (Apoli's {@code add_xp}.)
 *
 * <p>JSON: {@code { "type":"pheno:add_xp", "points":10 }}</p>
 */
public final class AddXpActionType implements ActionType {

    public static final String KEY = "pheno:add_xp";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int points = GsonHelper.getAsInt(json, "points", 0);
        int levels = GsonHelper.getAsInt(json, "levels", 0);
        if (points == 0 && levels == 0) return null;
        return ctx -> {
            if (ctx.entity() instanceof Player player) {
                if (points != 0) player.giveExperiencePoints(points);
                if (levels != 0) player.giveExperienceLevels(levels);
            }
        };
    }
}
