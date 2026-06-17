package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;

/**
 * The actor starts riding the target (Apoli's bi-entity {@code mount}). Server-side;
 * no-op without a target. Wrap in {@code invert} to make the target ride the actor.
 *
 * <p>JSON: {@code { "type":"pheno:mount" }}</p>
 */
public final class MountActionType implements ActionType {

    public static final String KEY = "pheno:mount";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.other() != null && !ctx.entity().level().isClientSide) {
                ctx.entity().startRiding(ctx.other(), true);
            }
        };
    }
}
