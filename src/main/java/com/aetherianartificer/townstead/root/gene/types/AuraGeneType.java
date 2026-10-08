package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A passive aura: runs an {@link Action} on every living entity within {@code radius}
 * every {@code interval} ticks (a healing aura for allies, a damaging aura for
 * everything nearby). Optionally gated by a {@link Condition} on the holder, and can
 * include the holder itself.
 *
 * <p>{@code target} filters who the action runs on: {@code all} (default),
 * {@code hostile} (monsters only), or {@code non_hostile} (villagers, players,
 * animals) — a healing aura must not mend the raider it stands beside.</p>
 *
 * <p>{@code resource_cost} makes each pulse spend a resource meter (paid once per
 * pulse that reaches at least one target; a pulse the holder cannot afford is
 * skipped), so a sustained aura competes with whatever else spends that meter.</p>
 *
 * <p>JSON: {@code { "type":"pheno:aura", "radius":4, "interval":40,
 * "include_self":false, "target":"non_hostile",
 * "resource_cost":{ "resource":"my_pack:stamina", "amount":30 },
 * "action":{ "type":"pheno:apply_effect",
 * "effect":"minecraft:regeneration", "duration":60 } }}</p>
 */
public final class AuraGeneType implements GeneType {

    public static final String KEY = "pheno:aura";

    public record Instance(double radius, int interval, Action action,
                           @Nullable Condition condition, boolean includeSelf,
                           String target, @Nullable net.minecraft.resources.ResourceLocation costResource,
                           int costAmount)
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
        double radius = Math.max(0.5, GsonHelper.getAsDouble(json, "radius", 4d));
        int interval = Math.max(10, GsonHelper.getAsInt(json, "interval", 40));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        boolean includeSelf = GsonHelper.getAsBoolean(json, "include_self", false);
        String target = GsonHelper.getAsString(json, "target", "all").toLowerCase(java.util.Locale.ROOT);
        net.minecraft.resources.ResourceLocation costResource = null;
        int costAmount = 0;
        if (json.has("resource_cost") && json.get("resource_cost").isJsonObject()) {
            JsonObject cost = json.getAsJsonObject("resource_cost");
            costResource = com.aetherianartificer.townstead.data.DataPackLang.parseId(
                    GsonHelper.getAsString(cost, "resource", ""));
            costAmount = GsonHelper.getAsInt(cost, "amount", 0);
        }
        return new Instance(radius, interval, action, condition, includeSelf, target, costResource, costAmount);
    }
}
