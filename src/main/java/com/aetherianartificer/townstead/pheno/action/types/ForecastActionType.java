package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * Tells the bearer what the weather is and how long it lasts, above the hotbar. Nothing happens
 * when the bearer is not a player.
 *
 * <p>JSON: {@code { "type":"pheno:forecast" }}</p>
 */
public final class ForecastActionType implements ActionType {

    public static final String KEY = "pheno:forecast";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.entity() instanceof ServerPlayer viewer) || !(viewer.level() instanceof ServerLevel level)) return;
            ServerLevelData data = level.getServer().getWorldData().overworldData();
            String kind;
            String fallback;
            int ticks;
            if (data.isThundering()) {
                kind = "thunder";
                fallback = "Thunderstorm for %s";
                ticks = data.getThunderTime();
            } else if (data.isRaining()) {
                kind = "rain";
                fallback = "Rain for %s";
                ticks = data.getRainTime();
            } else {
                kind = "clear";
                fallback = "Clear for %s";
                ticks = data.getClearWeatherTime() > 0 ? data.getClearWeatherTime() : data.getRainTime();
            }
            int seconds = Math.max(0, ticks) / 20;
            String time = seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
            viewer.displayClientMessage(Component.translatableWithFallback("pheno.forecast." + kind, fallback, time), true);
        };
    }
}
