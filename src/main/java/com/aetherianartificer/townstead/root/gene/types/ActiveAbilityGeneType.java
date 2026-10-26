package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A keybind-triggered origin ability: runs an {@link Action} on activation, subject
 * to a {@code cooldown} (ticks) and an optional {@link Condition} gate. {@code slot}
 * binds it to one of the pooled "Root Ability" keys (1-based; omit or 0 to
 * auto-assign). {@code ai_trigger} lets villagers use it on their own (default
 * {@code never}, so villagers carry the gene but only players fire it unless opted in).
 *
 * <p>JSON: {@code { "type":"pheno:active_ability", "cooldown":200,
 * "slot":1, "ai_trigger":"when_hurt",
 * "action":{ "type":"pheno:heal", "amount":6 } }}</p>
 *
 * <p>{@code windup} delays the action: a number of ticks, or {@code { "ticks":10, "slow":0.7,
 * "interruptible":true, "action":{...}, "on_interrupt":{...} }} where {@code slow} is the movement
 * speed kept while winding up, {@code action} runs as the wind-up starts and an interruptible
 * wind-up is cancelled by taking damage. The cooldown starts on the press; an interrupted
 * wind-up gives it back and charges nothing.</p>
 */
public final class ActiveAbilityGeneType implements GeneType {

    public static final String KEY = "pheno:active_ability";

    public enum AiTrigger {
        NEVER, ALWAYS, WHEN_HURT, WHEN_THREATENED, WHEN_FLYING, WHEN_HURT_NEARBY;

        public static AiTrigger byKey(String raw) {
            if (raw == null) return NEVER;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "always" -> ALWAYS;
                case "when_hurt", "hurt" -> WHEN_HURT;
                case "when_threatened", "threatened" -> WHEN_THREATENED;
                case "when_flying", "flying" -> WHEN_FLYING;
                case "when_hurt_nearby", "hurt_nearby" -> WHEN_HURT_NEARBY;
                default -> NEVER;
            };
        }
    }

    public record Instance(Action action, int cooldownTicks, @Nullable Condition condition,
                           int slot, AiTrigger aiTrigger,
                           @Nullable ResourceLocation costResource, int costAmount,
                           com.aetherianartificer.townstead.pheno.combat.Casts.@Nullable Windup windup)
            implements GeneInstance, com.aetherianartificer.townstead.root.ability.ResourceConsumer {
        public Instance(Action action, int cooldownTicks, @Nullable Condition condition, int slot,
                        AiTrigger aiTrigger, @Nullable ResourceLocation costResource, int costAmount) {
            this(action, cooldownTicks, condition, slot, aiTrigger, costResource, costAmount, null);
        }

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
        int cooldown = Math.max(0, GsonHelper.getAsInt(json, "cooldown", 20));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        int slot = GsonHelper.getAsInt(json, "slot", 0);
        AiTrigger aiTrigger = AiTrigger.byKey(GsonHelper.getAsString(json, "ai_trigger", "never"));
        ResourceLocation costResource = null;
        int costAmount = 0;
        if (json.has("resource_cost") && json.get("resource_cost").isJsonObject()) {
            JsonObject cost = json.getAsJsonObject("resource_cost");
            costResource = DataPackLang.parseId(GsonHelper.getAsString(cost, "resource", ""));
            costAmount = GsonHelper.getAsInt(cost, "amount", 0);
        }
        com.aetherianartificer.townstead.pheno.combat.Casts.Windup windup = null;
        if (json.has("windup")) {
            windup = parseWindup(json.get("windup"));
            if (windup == null) return null;
        }
        return new Instance(action, cooldown, condition, slot, aiTrigger, costResource, costAmount, windup);
    }

    private static com.aetherianartificer.townstead.pheno.combat.Casts.@Nullable Windup parseWindup(
            com.google.gson.JsonElement raw) {
        if (raw.isJsonPrimitive()) {
            int ticks = raw.getAsInt();
            return ticks > 0 ? new com.aetherianartificer.townstead.pheno.combat.Casts.Windup(
                    ticks, 1f, false, null, null) : null;
        }
        if (!raw.isJsonObject()) return null;
        JsonObject json = raw.getAsJsonObject();
        int ticks = GsonHelper.getAsInt(json, "ticks", 0);
        if (ticks <= 0) return null;
        float slow = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "slow", 1f)));
        boolean interruptible = GsonHelper.getAsBoolean(json, "interruptible", false);
        Action onStart = json.has("action") ? Actions.parse(json.get("action")) : null;
        Action onInterrupt = json.has("on_interrupt") ? Actions.parse(json.get("on_interrupt")) : null;
        if ((json.has("action") && onStart == null) || (json.has("on_interrupt") && onInterrupt == null)) return null;
        return new com.aetherianartificer.townstead.pheno.combat.Casts.Windup(
                ticks, slow, interruptible, onStart, onInterrupt);
    }
}
