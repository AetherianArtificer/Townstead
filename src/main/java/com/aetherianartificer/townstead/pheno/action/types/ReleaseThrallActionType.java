package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.compat.vampirism.Thralls;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;

/**
 * Breaks the entity's thrall bond by force: the bond ends and the thrall state is cleared, so
 * they serve no one. Fails when the entity is no one's thrall.
 * <pre>
 * { "type": "pheno:release_thrall" }
 * </pre>
 */
public final class ReleaseThrallActionType implements ActionType {
    public static final String KEY = "pheno:release_thrall";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.entity() == null || !Thralls.release(ctx.entity())) ctx.fail();
        };
    }
}
