package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A passive aura: runs an {@link Action} on every living entity within {@code radius}
 * every {@code interval} ticks (a healing aura for allies, a damaging aura for
 * everything nearby). Optionally gated by a {@link Condition} on the holder, and can
 * include the holder itself.
 *
 * <p>JSON: {@code { "type":"pheno:aura", "radius":4, "interval":40,
 * "include_self":false, "action":{ "type":"pheno:apply_effect",
 * "effect":"minecraft:regeneration", "duration":60 } }}</p>
 */
public final class AuraGeneType implements GeneType {

    public static final String KEY = "pheno:aura";

    public record Instance(double radius, int interval, Action action,
                           @Nullable Condition condition, boolean includeSelf) implements GeneInstance {
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
        return new Instance(radius, interval, action, condition, includeSelf);
    }
}
