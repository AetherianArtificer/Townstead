package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;

/** Reduces a Townstead villager's fatigue by a data-defined amount. */
public final class EnergizeActionType implements ActionType {
    public static final String KEY = "pheno:energize";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        Value amount = json.has("amount") ? Values.parse(json.get("amount")) : Values.constant(0);
        if (amount == null) return null;
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return;
            if (!TownsteadConfig.isVillagerFatigueEnabled()) return;
            int value = Math.max(0, (int) Math.round(amount.get(SelectorContext.of(ctx))));
            if (value > 0) TownsteadVillagers.get(villager).needs().restoreEnergy(value);
        };
    }
}
