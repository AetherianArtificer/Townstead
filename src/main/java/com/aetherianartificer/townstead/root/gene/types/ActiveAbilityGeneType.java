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
 *
 * <p>{@code hold} makes it fire again and again while its key is held, as often as its cooldown allows and paying
 * its cost each time: a hose, a stream, a beam you sweep. Fired from the wheel it streams for a moment.</p>
 *
 * <p>{@code modes} are other faces of the same ability: each has a {@code when} condition and its own
 * {@code display_name}, {@code description} and {@code icon}, and the wheel shows the first one that holds (or the
 * ability's own look when none does). {@code on_enter} runs on the bearer the moment one starts to hold, as a cue:
 * {@code "modes":[{ "when":{ "type":"pheno:resource", "resource":"examplepack:mana", "min":70 },
 * "display_name":"Firestorm", "icon":"minecraft:fire_charge", "on_enter":{ "type":"pheno:play_sound", ... } }]}.
 * What the ability does in each is up to its action, usually an {@code if_else} on the same condition.</p>
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
                           com.aetherianartificer.townstead.pheno.combat.Casts.@Nullable Windup windup,
                           boolean hold, java.util.List<Mode> modes)
            implements GeneInstance, com.aetherianartificer.townstead.root.ability.ResourceConsumer {
        public Instance(Action action, int cooldownTicks, @Nullable Condition condition, int slot,
                        AiTrigger aiTrigger, @Nullable ResourceLocation costResource, int costAmount) {
            this(action, cooldownTicks, condition, slot, aiTrigger, costResource, costAmount, null, false,
                    java.util.List.of());
        }

        /** Which mode holds for this bearer now, or -1 for the ability's own look. */
        public int modeOf(net.minecraft.world.entity.LivingEntity bearer) {
            if (modes.isEmpty()) return -1;
            var ctx = new com.aetherianartificer.townstead.pheno.condition.ConditionContext(bearer);
            for (int i = 0; i < modes.size(); i++) {
                if (modes.get(i).when().test(ctx)) return i;
            }
            return -1;
        }

        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    /** One face of an ability: when it holds, what the wheel calls and draws it, and what plays as it starts. */
    public record Mode(Condition when, @Nullable com.google.gson.JsonElement name,
                       @Nullable com.google.gson.JsonElement description, @Nullable ResourceLocation icon,
                       @Nullable Action onEnter) {

        /** The authored name, or null to keep the ability's own. Read when asked, once the pack's text has loaded. */
        public net.minecraft.network.chat.@Nullable Component displayName() {
            return text(name);
        }

        public net.minecraft.network.chat.@Nullable Component displayDescription() {
            return text(description);
        }

        private static net.minecraft.network.chat.@Nullable Component text(@Nullable com.google.gson.JsonElement el) {
            if (el == null || el.isJsonNull()) return null;
            if (el.isJsonPrimitive()) return net.minecraft.network.chat.Component.literal(el.getAsString());
            if (el.isJsonObject() && el.getAsJsonObject().has("translate")) {
                String key = el.getAsJsonObject().get("translate").getAsString();
                return net.minecraft.network.chat.Component.translatableWithFallback(key,
                        DataPackLang.resolveFallback(key, "en_us", key));
            }
            if (el.isJsonObject() && el.getAsJsonObject().has("text")) {
                return net.minecraft.network.chat.Component.literal(el.getAsJsonObject().get("text").getAsString());
            }
            return null;
        }
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
        boolean hold = GsonHelper.getAsBoolean(json, "hold", false);
        java.util.List<Mode> modes = new java.util.ArrayList<>();
        if (json.has("modes") && json.get("modes").isJsonArray()) {
            for (com.google.gson.JsonElement raw : json.getAsJsonArray("modes")) {
                if (!raw.isJsonObject()) continue;
                JsonObject mode = raw.getAsJsonObject();
                Condition when = mode.has("when") ? Conditions.parse(mode.get("when")) : null;
                if (when == null) continue;
                ResourceLocation icon = mode.has("icon") ? DataPackLang.parseId(GsonHelper.getAsString(mode, "icon", "")) : null;
                Action onEnter = mode.has("on_enter") ? Actions.parse(mode.get("on_enter")) : null;
                modes.add(new Mode(when, mode.get("display_name"), mode.get("description"), icon, onEnter));
            }
        }
        return new Instance(action, cooldown, condition, slot, aiTrigger, costResource, costAmount, windup, hold,
                java.util.List.copyOf(modes));
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
