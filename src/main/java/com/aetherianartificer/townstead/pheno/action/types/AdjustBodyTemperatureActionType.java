package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.player.Player;

/**
 * {@code pheno:warm} raises and {@code pheno:cool} lowers body temperature by {@code amount}
 * degrees Celsius. Hot soup warms, an iced drink cools. A villager's own temperature need takes
 * it directly; a player's goes to whichever temperature mod owns their meter.
 */
public final class AdjustBodyTemperatureActionType implements ActionType {
    public static final String WARM = "pheno:warm";
    public static final String COOL = "pheno:cool";

    private final String key;
    private final float sign;

    public AdjustBodyTemperatureActionType(String key, boolean warm) {
        this.key = key;
        this.sign = warm ? 1f : -1f;
    }

    @Override public String key() { return key; }

    @Override
    public Action parse(JsonObject json) {
        Value amount = json.has("amount") ? Values.parse(json.get("amount")) : Values.constant(0);
        if (amount == null) return null;
        // Ticks, like every other duration in this schema. Zero is an instant change the
        // backend lets decay; above zero holds the influence for that long.
        int duration = Math.max(0, GsonHelper.getAsInt(json, "duration", 0));
        return ctx -> {
            float degrees = (float) amount.get(SelectorContext.of(ctx));
            if (degrees <= 0f) return;
            if (ctx.entity() instanceof VillagerEntityMCA villager) {
                if (!TownsteadConfig.isVillagerTemperatureEnabled()
                        || NeedSuppression.suppressesTemperature(villager)) return;
                // A villager's own body temperature already decays toward ambient, so the
                // instant change is the whole of it and duration has nothing to add.
                TownsteadVillagers.get(villager).needs().adjustBodyTemp(TemperatureData.tenths(degrees * sign));
            } else if (ctx.entity() instanceof Player player) {
                // Players have no Townstead temperature need; whichever mod owns theirs takes it.
                AmbientTemperatureBridge bridge = TemperatureBridgeResolver.get();
                if (bridge != null) bridge.adjustPlayerBodyCelsius(player, degrees * sign, duration);
            }
        };
    }
}
