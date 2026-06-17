package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * True when the entity is a player in the given {@code gamemode} ({@code survival},
 * {@code creative}, {@code adventure}, {@code spectator}); Apoli's {@code gamemode}.
 *
 * <p>JSON: {@code { "type":"pheno:gamemode", "gamemode":"creative" }}</p>
 */
public final class GamemodeConditionType implements ConditionType {

    public static final String KEY = "pheno:gamemode";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String mode = GsonHelper.getAsString(json, "gamemode", "").toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) return null;
        return ctx -> ctx.entity() instanceof ServerPlayer player
                && player.gameMode.getGameModeForPlayer().getName().equals(mode);
    }
}
