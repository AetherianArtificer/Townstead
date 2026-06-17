package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * An edge-triggered threshold action on a rising counter (a {@code resource} meter or a
 * {@code collection} member's tally). {@code at} fires once when the count crosses that value
 * upward; {@code every} fires each time it crosses a multiple (a repeating milestone). {@code then:
 * reset} (resource only) returns the meter to its start after firing, the "consume the meter" half
 * of a charge-and-spend loop.
 *
 * <p>JSON: {@code { "at":100, "do":{...} }} or {@code { "every":50, "do":{...}, "then":"reset" }}</p>
 */
public record ReachHook(int at, int every, Action action, boolean reset) {

    /** Whether a rise from {@code prev} to {@code next} should fire this hook. Fires once per change. */
    public boolean crossed(int prev, int next) {
        if (next <= prev) return false;
        if (every > 0) return Math.floorDiv(next, every) > Math.floorDiv(prev, every);
        return prev < at && next >= at;
    }

    public static List<ReachHook> parseList(JsonElement element) {
        List<ReachHook> hooks = new ArrayList<>();
        if (element == null) return hooks;
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) addOne(hooks, e);
        } else {
            addOne(hooks, element);
        }
        return hooks;
    }

    private static void addOne(List<ReachHook> hooks, JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject obj = element.getAsJsonObject();
        Action action = obj.has("do") ? Actions.parse(obj.get("do")) : null;
        if (action == null) return;
        int every = GsonHelper.getAsInt(obj, "every", 0);
        int at = GsonHelper.getAsInt(obj, "at", 0);
        if (every <= 0 && at == 0) return;
        boolean reset = "reset".equalsIgnoreCase(GsonHelper.getAsString(obj, "then", ""));
        hooks.add(new ReachHook(at, every, action, reset));
    }
}
