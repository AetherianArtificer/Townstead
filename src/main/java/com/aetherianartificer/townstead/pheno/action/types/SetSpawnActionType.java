package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code pheno:set_spawn}: sets a player's respawn point to where they are, as a bed would.
 *
 * <p>JSON: {@code { "type":"pheno:set_spawn" }}</p>
 */
public final class SetSpawnActionType implements ActionType {
    public static final String KEY = "pheno:set_spawn";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.entity() instanceof ServerPlayer player)) {
                ctx.fail();
                return;
            }
            player.setRespawnPosition(player.level().dimension(), player.blockPosition(), player.getYRot(), true, true);
        };
    }
}
