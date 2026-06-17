package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

/**
 * Puts the target animal into love mode (Apoli's bi-entity {@code set_in_love}); the
 * actor, if a player, is credited as the cause for breeding stats. Server-side; no-op
 * if the target is not an animal.
 *
 * <p>JSON: {@code { "type":"pheno:set_in_love" }}</p>
 */
public final class SetInLoveActionType implements ActionType {

    public static final String KEY = "pheno:set_in_love";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.other() instanceof Animal animal && !animal.level().isClientSide) {
                animal.setInLove(ctx.entity() instanceof Player player ? player : null);
            }
        };
    }
}
