package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;

/**
 * Drops the actor's entire inventory on the ground (player-only; Apoli's
 * {@code drop_inventory}). A curse-flavored trigger effect.
 *
 * <p>JSON: {@code { "type":"pheno:drop_inventory" }}</p>
 */
public final class DropInventoryActionType implements ActionType {

    public static final String KEY = "pheno:drop_inventory";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.entity() instanceof Player player) player.getInventory().dropAll();
        };
    }
}
