package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.player.Player;

/**
 * Restores the immediate and lasting portions of hydration: a villager's own Townstead need,
 * or a player's meter in whichever thirst mod owns it.
 */
public final class HydrateActionType implements ActionType {
    public static final String KEY = "pheno:hydrate";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        Value immediate = json.has("immediate") ? Values.parse(json.get("immediate")) : Values.constant(0);
        Value lasting = json.has("lasting") ? Values.parse(json.get("lasting")) : Values.constant(0);
        if (immediate == null || lasting == null) return null;
        return ctx -> {
            SelectorContext values = SelectorContext.of(ctx);
            int immediateAmount = Math.max(0, (int) Math.round(immediate.get(values)));
            int lastingAmount = Math.max(0, (int) Math.round(lasting.get(values)));
            if (immediateAmount == 0 && lastingAmount == 0) return;
            if (ctx.entity() instanceof VillagerEntityMCA villager) {
                if (!TownsteadConfig.isVillagerThirstEnabled()
                        || NeedSuppression.suppressesThirst(villager)) return;
                var needs = TownsteadVillagers.get(villager).needs();
                needs.applyDrink(immediateAmount, lastingAmount, true);
                needs.setLastDrankTime(villager.level().getGameTime());
            } else if (ctx.entity() instanceof Player player) {
                // Players have no Townstead thirst meter; whichever thirst mod owns theirs
                // takes the restore. No thirst mod means there is nothing to restore, which
                // is the correct outcome rather than a gap.
                ThirstCompatBridge bridge = ThirstBridgeResolver.get();
                if (bridge != null) bridge.restorePlayerThirst(player, immediateAmount, lastingAmount);
            }
        };
    }
}
