package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Restores the meter that gates how much work a character has left in them: a villager's
 * Townstead fatigue need, or a player's {@code townstead:stamina}. Players carry no fatigue
 * need, and stamina is the baseline meter every character has and every ability spends from,
 * so it is the honest counterpart rather than a second bar nobody sees.
 */
public final class EnergizeActionType implements ActionType {
    public static final String KEY = "pheno:energize";

    /** The baseline meter from {@code data/townstead/baseline_power/stamina.json}. */
    private static final String PLAYER_METER = "townstead:stamina";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        Value amount = json.has("amount") ? Values.parse(json.get("amount")) : Values.constant(0);
        if (amount == null) return null;
        return ctx -> {
            int value = Math.max(0, (int) Math.round(amount.get(SelectorContext.of(ctx))));
            if (value <= 0) return;
            if (ctx.entity() instanceof VillagerEntityMCA villager) {
                if (!TownsteadConfig.isVillagerFatigueEnabled()) return;
                TownsteadVillagers.get(villager).needs().restoreEnergy(value);
            } else if (ctx.entity() instanceof Player player) {
                ResourceLocation meter = DataPackLang.parseId(PLAYER_METER);
                if (meter != null) ResourceValues.change(player, meter, value);
            }
        };
    }
}
