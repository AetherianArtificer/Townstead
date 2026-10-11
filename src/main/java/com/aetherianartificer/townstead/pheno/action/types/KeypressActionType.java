package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Presses a key for the entity: runs its {@code press} triggers bound to {@code key}, exactly as the
 * player's own keypress does. A villager has no keyboard, so an {@code ai} trigger can press for it
 * and reach a power that players drive with a key (the jump that beats a glider's wings).
 *
 * <p>JSON: {@code { "type":"pheno:keypress", "key":"jump" }}</p>
 */
public final class KeypressActionType implements ActionType {

    public static final String KEY = "pheno:keypress";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        if (key.isEmpty()) return null;
        return ctx -> com.aetherianartificer.townstead.root.trigger.GeneTriggers.firePress(ctx.entity(), key);
    }
}
