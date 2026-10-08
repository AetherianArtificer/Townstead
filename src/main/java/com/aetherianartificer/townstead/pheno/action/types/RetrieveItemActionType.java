package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.ItemRetrievals;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** A visible extending/retracting tether that retrieves one eligible dropped item. */
public final class RetrieveItemActionType implements ActionType {
    public static final String KEY = "pheno:retrieve_item";
    @Override public String key() { return KEY; }
    @Override public Action parse(JsonObject json) {
        double range = GsonHelper.getAsDouble(json, "range", 6);
        double speed = GsonHelper.getAsDouble(json, "speed", .65);
        double aim = GsonHelper.getAsDouble(json, "aim_radius", .35);
        int ticks = GsonHelper.getAsInt(json, "extend_ticks", 4);
        String color = GsonHelper.getAsString(json, "color", "#E58D9A");
        if (!Double.isFinite(range) || range < 1 || range > 16
                || !Double.isFinite(speed) || speed < .1 || speed > 1
                || !Double.isFinite(aim) || aim < 0 || aim > 1 || ticks < 1 || ticks > 20
                || !color.matches("#[0-9a-fA-F]{6}")) return null;
        int rgb = Integer.parseInt(color.substring(1), 16);
        return ctx -> {
            if (!ItemRetrievals.start(ctx.entity(), range, speed, aim, ticks, rgb)) ctx.fail();
        };
    }
}
