package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;

/** Restores the immediate and lasting portions of a Townstead villager's hydration. */
public final class HydrateActionType implements ActionType {
    public static final String KEY = "pheno:hydrate";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        Value immediate = json.has("immediate") ? Values.parse(json.get("immediate")) : Values.constant(0);
        Value lasting = json.has("lasting") ? Values.parse(json.get("lasting")) : Values.constant(0);
        if (immediate == null || lasting == null) return null;
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return;
            if (!TownsteadConfig.isVillagerThirstEnabled() || NeedSuppression.suppressesThirst(villager)) return;
            SelectorContext values = SelectorContext.of(ctx);
            int immediateAmount = Math.max(0, (int) Math.round(immediate.get(values)));
            int lastingAmount = Math.max(0, (int) Math.round(lasting.get(values)));
            if (immediateAmount == 0 && lastingAmount == 0) return;
            var needs = TownsteadVillagers.get(villager).needs();
            needs.applyDrink(immediateAmount, lastingAmount, true);
            needs.setLastDrankTime(villager.level().getGameTime());
        };
    }
}
