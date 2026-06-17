package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * The actor tames the target if it is a tamable animal and the actor is a player
 * (Apoli's bi-entity {@code tame}). Server-side; no-op otherwise. A racial
 * animal-whisperer trait paired with a {@code when_attack} / interaction trigger.
 *
 * <p>JSON: {@code { "type":"pheno:tame" }}</p>
 */
public final class TameActionType implements ActionType {

    public static final String KEY = "pheno:tame";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.entity() instanceof Player player && ctx.other() instanceof TamableAnimal animal
                    && !animal.isTame() && !animal.level().isClientSide) {
                animal.tame(player);
            }
        };
    }
}
