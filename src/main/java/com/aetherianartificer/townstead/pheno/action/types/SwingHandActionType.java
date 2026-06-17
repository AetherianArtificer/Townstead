package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;

/**
 * Plays the actor's hand-swing animation (Apoli's {@code swing_hand}). {@code hand} is
 * {@code main} (default) or {@code off}.
 *
 * <p>JSON: {@code { "type":"pheno:swing_hand", "hand":"main" }}</p>
 */
public final class SwingHandActionType implements ActionType {

    public static final String KEY = "pheno:swing_hand";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        InteractionHand hand = "off".equalsIgnoreCase(GsonHelper.getAsString(json, "hand", "main"))
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return ctx -> ctx.entity().swing(hand);
    }
}
