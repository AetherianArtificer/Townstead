package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.hazard.AvoidStrategy;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a self-{@link Action} on the entity every {@code interval} ticks, optionally
 * gated by a {@link Condition} (Apoli {@code action_over_time}). The periodic
 * "self-effect" backbone: {@code ignite} = burn, {@code freeze} = freeze,
 * {@code exhaust} = drain hunger, plus any other action (damage, apply_effect, …).
 * Applied server-side by {@code GeneAbilityTicker}. An optional {@code resource_cost} checks and
 * spends a meter before each run. An amount of zero records the resource reference for HUD
 * visibility without spending it.
 *
 * <p>JSON: {@code { "type":"pheno:action_over_time", "interval":20,
 * "action":{ "type":"pheno:ignite", "seconds":2 },
 * "condition":{ "type":"pheno:environment", "exposure":"sky" } }}</p>
 */
public final class ActionOverTimeGeneType implements GeneType {

    public static final String KEY = "pheno:action_over_time";

    public record Instance(Action action, int interval, @Nullable Condition condition,
                           @Nullable AvoidStrategy avoid,
                           @Nullable ResourceLocation costResource, int costAmount)
            implements GeneInstance, com.aetherianartificer.townstead.root.ability.ResourceConsumer {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Action action = Actions.parse(json.get("action"));
        if (action == null) return null;
        int interval = Math.max(1, GsonHelper.getAsInt(json, "interval", 20));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        ResourceLocation costResource = null;
        int costAmount = 0;
        if (json.has("resource_cost") && json.get("resource_cost").isJsonObject()) {
            JsonObject cost = json.getAsJsonObject("resource_cost");
            costResource = com.aetherianartificer.townstead.data.DataPackLang.parseId(
                    GsonHelper.getAsString(cost, "resource", ""));
            costAmount = Math.max(0, GsonHelper.getAsInt(cost, "amount", 0));
        }
        // The "damage source" derived once here and cached on the parsed gene: a harmful periodic
        // action under a positional condition becomes a spatial hazard the AI flees (GeneAbilityTicker).
        AvoidStrategy avoid = AvoidStrategy.derive(json.get("action"), json.get("condition"));
        return new Instance(action, interval, condition, avoid, costResource, costAmount);
    }
}
