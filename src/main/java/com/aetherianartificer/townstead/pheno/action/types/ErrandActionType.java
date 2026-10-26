package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.journey.Journeys;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Sends the villager side of the action away on an errand. They leave, and walk back into their
 * village after {@code days}. With {@code adopt}, they come back with a new pet of that type
 * ({@code essential}: downed instead of killed). With {@code leave_pets}, their pets stay home.
 * <pre>
 * { "type": "pheno:errand", "days": 1, "adopt": "minecraft:wolf", "essential": true, "leave_pets": false }
 * </pre>
 */
public final class ErrandActionType implements ActionType {
    public static final String KEY = "pheno:errand";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int days = Math.max(0, GsonHelper.getAsInt(json, "days", 1));
        ResourceLocation adopt = json.has("adopt") ? DataPackLang.parseId(GsonHelper.getAsString(json, "adopt")) : null;
        boolean essential = GsonHelper.getAsBoolean(json, "essential", false);
        boolean leavePets = GsonHelper.getAsBoolean(json, "leave_pets", false);
        return ctx -> {
            VillagerEntityMCA villager = ctx.entity() instanceof VillagerEntityMCA v ? v
                    : ctx.other() instanceof VillagerEntityMCA v ? v : null;
            if (villager == null || !Journeys.errand(villager, days, adopt, essential, leavePets)) ctx.fail();
        };
    }
}
