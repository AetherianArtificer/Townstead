package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.combat.Dashes;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Dashes the focus {@code distance} blocks along its look over {@code ticks} ticks. A
 * {@code horizontal} dash (the default) ignores pitch and keeps to the ground; otherwise the dash
 * follows the full look, for flyers and swimmers. Walls end it early. {@code on_hit} runs on each
 * creature in the path (up to {@code max_hits}, default 1, never allies), with the dasher as the
 * counterpart, and {@code stop_on_hit} (default true) ends the dash at the first. {@code on_end}
 * runs on the dasher when it stops. A dash that cannot move at all fails, so its ability keeps
 * its cooldown.
 *
 * <p>JSON: {@code { "type":"pheno:dash", "distance":5, "ticks":10,
 * "on_hit":{ "type":"pheno:damage", "amount":6 } }}</p>
 */
public final class DashActionType implements ActionType {

    public static final String KEY = "pheno:dash";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        double distance = GsonHelper.getAsDouble(json, "distance", 4);
        int ticks = GsonHelper.getAsInt(json, "ticks", 8);
        if (!Double.isFinite(distance) || distance <= 0 || distance > 32 || ticks < 1 || ticks > 100) return null;
        boolean horizontal = GsonHelper.getAsBoolean(json, "horizontal", true);
        boolean stopOnHit = GsonHelper.getAsBoolean(json, "stop_on_hit", true);
        int maxHits = Math.max(1, GsonHelper.getAsInt(json, "max_hits", 1));
        double hitRadius = Math.max(0, GsonHelper.getAsDouble(json, "hit_radius", 0.4));
        Action onHit = json.has("on_hit") ? Actions.parse(json.get("on_hit")) : null;
        Action onEnd = json.has("on_end") ? Actions.parse(json.get("on_end")) : null;
        if ((json.has("on_hit") && onHit == null) || (json.has("on_end") && onEnd == null)) return null;
        Dashes.Spec spec = new Dashes.Spec(distance, ticks, horizontal, stopOnHit, maxHits, hitRadius, onHit, onEnd);
        return ctx -> {
            if (!Dashes.start(ctx.entity(), spec)) ctx.fail();
        };
    }
}
