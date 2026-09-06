package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/** An independently contributed set of Pheno hooks for an open state. */
public record StateEffect(ResourceLocation id, ResourceLocation state, @Nullable String tier,
                          int priority, @Nullable Action onEnter, @Nullable Action onTierChange,
                          @Nullable Periodic whileActive, @Nullable Action onExit) {
    public static final String SCHEMA = "pheno:state_effect/v1";
    public record Periodic(int interval, double chance, Action action) {}

    static StateEffect parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        ResourceLocation state = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (state == null) throw new IllegalArgumentException("'state' must be a resource id");
        String tier = json.has("tier") ? GsonHelper.getAsString(json, "tier", "").trim() : null;
        if (tier != null && tier.isEmpty()) throw new IllegalArgumentException("'tier' cannot be empty");
        Action enter = action(json, "on_enter");
        Action change = action(json, "on_tier_change");
        Action exit = action(json, "on_exit");
        Periodic periodic = null;
        if (json.has("while_active")) {
            if (!json.get("while_active").isJsonObject()) throw new IllegalArgumentException("'while_active' must be an object");
            JsonObject loop = json.getAsJsonObject("while_active");
            int interval = GsonHelper.getAsInt(loop, "interval", 20);
            double chance = GsonHelper.getAsDouble(loop, "chance", 1);
            Action action = Actions.parse(loop.get("do"));
            if (interval < 1) throw new IllegalArgumentException("while_active interval must be positive");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("while_active chance must be in [0,1]");
            if (action == null) throw new IllegalArgumentException("while_active requires a valid 'do' action");
            periodic = new Periodic(interval, chance, action);
        }
        if (enter == null && change == null && exit == null && periodic == null) {
            throw new IllegalArgumentException("state effect has no hooks");
        }
        return new StateEffect(id, state, tier, GsonHelper.getAsInt(json, "priority", 0),
                enter, change, periodic, exit);
    }

    private static @Nullable Action action(JsonObject json, String key) {
        if (!json.has(key)) return null;
        JsonElement element = json.get(key);
        Action action = Actions.parse(element);
        if (action == null) throw new IllegalArgumentException("'" + key + "' is not a valid Pheno action");
        return action;
    }
}
