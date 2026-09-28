package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Leaps;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** A grounded, clearance-checked leap. Failed attempts spend no ability cooldown. */
public final class LeapActionType implements ActionType {
    public static final String KEY = "pheno:leap";
    @Override public String key() { return KEY; }

    @Override public Action parse(JsonObject json) {
        double up = GsonHelper.getAsDouble(json, "upward_velocity", .7);
        double forward = GsonHelper.getAsDouble(json, "forward_velocity", .18);
        if (!Double.isFinite(up) || !Double.isFinite(forward)
                || up < .1 || up > 1.2 || forward < 0 || forward > .5) return null;
        return ctx -> { if (!Leaps.start(ctx.entity(), up, forward)) ctx.fail(); };
    }
}
