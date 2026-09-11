package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * {@code pheno:warm} raises and {@code pheno:cool} lowers a Townstead villager's body temperature by
 * {@code amount} degrees Celsius. Hot soup warms, an iced drink cools.
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
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return;
            if (!TownsteadConfig.isVillagerTemperatureEnabled() || NeedSuppression.suppressesTemperature(villager)) return;
            float degrees = (float) amount.get(SelectorContext.of(ctx));
            if (degrees <= 0f) return;
            TownsteadVillagers.get(villager).needs().adjustBodyTemp(TemperatureData.tenths(degrees * sign));
        };
    }
}
