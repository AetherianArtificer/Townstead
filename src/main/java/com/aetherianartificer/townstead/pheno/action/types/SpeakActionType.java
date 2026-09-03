package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;

/** A data-authored MCA speech pool usable by states, reactions, work, and hangouts. */
public final class SpeakActionType implements ActionType {
    public static final String KEY = "pheno:speak";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        String pool = GsonHelper.getAsString(json, "pool", "").trim();
        if (pool.isEmpty()) return null;
        boolean numbered = json.has("variants");
        int variants = Math.max(1, GsonHelper.getAsInt(json, "variants", 1));
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return;
            String phrase = numbered ? pool + "/" + (villager.getRandom().nextInt(variants) + 1) : pool;
            villager.sendChatToAllAround(phrase);
        };
    }
}
