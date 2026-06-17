package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A keybind-triggered origin ability: runs an {@link Action} on activation, subject
 * to a {@code cooldown} (ticks) and an optional {@link Condition} gate. {@code slot}
 * binds it to one of the pooled "Origin Ability" keys (1-based; omit or 0 to
 * auto-assign). {@code ai_trigger} lets villagers use it on their own (default
 * {@code never}, so villagers carry the gene but only players fire it unless opted in).
 *
 * <p>JSON: {@code { "type":"pheno:active_ability", "cooldown":200,
 * "slot":1, "ai_trigger":"when_hurt",
 * "action":{ "type":"pheno:heal", "amount":6 } }}</p>
 */
public final class ActiveAbilityGeneType implements GeneType {

    public static final String KEY = "pheno:active_ability";

    public enum AiTrigger {
        NEVER, ALWAYS, WHEN_HURT, WHEN_THREATENED;

        public static AiTrigger byKey(String raw) {
            if (raw == null) return NEVER;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "always" -> ALWAYS;
                case "when_hurt", "hurt" -> WHEN_HURT;
                case "when_threatened", "threatened" -> WHEN_THREATENED;
                default -> NEVER;
            };
        }
    }

    public record Instance(Action action, int cooldownTicks, @Nullable Condition condition,
                           int slot, AiTrigger aiTrigger,
                           @Nullable ResourceLocation costResource, int costAmount) implements GeneInstance {
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
        return new Instance(action, cooldown, condition, slot, aiTrigger, costResource, costAmount);
    }
}
