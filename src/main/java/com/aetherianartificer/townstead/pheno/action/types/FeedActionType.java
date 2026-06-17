package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Restores the actor's hunger and saturation (player-only; Apoli's {@code feed}).
 * {@code FoodData.eat(int,float)} is uniform across branches.
 *
 * <p>JSON: {@code { "type":"pheno:feed", "food":4, "saturation":2.0 }}</p>
 */
public final class FeedActionType implements ActionType {

    public static final String KEY = "pheno:feed";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int food = GsonHelper.getAsInt(json, "food", 1);
        float saturation = GsonHelper.getAsFloat(json, "saturation", 1.0f);
        return ctx -> {
            if (ctx.entity() instanceof Player player) player.getFoodData().eat(food, saturation);
        };
    }
}
