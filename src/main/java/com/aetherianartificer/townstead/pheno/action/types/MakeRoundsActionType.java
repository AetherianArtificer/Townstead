package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.journey.Rounds;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;

/**
 * The villager side of the action makes the rounds: they walk to up to {@code count} residents of
 * their village, nearest first, and stop with each for a moment.
 * <pre>
 * { "type": "pheno:make_rounds", "count": 8 }
 * </pre>
 */
public final class MakeRoundsActionType implements ActionType {
    public static final String KEY = "pheno:make_rounds";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 8));
        return ctx -> {
            VillagerEntityMCA villager = ctx.entity() instanceof VillagerEntityMCA v ? v
                    : ctx.other() instanceof VillagerEntityMCA v ? v : null;
            if (villager == null || !Rounds.start(villager, count)) ctx.fail();
        };
    }
}
