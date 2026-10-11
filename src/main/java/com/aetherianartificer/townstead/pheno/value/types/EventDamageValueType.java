package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.event.PhenoEvent;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * The damage of the event the current trigger answers ({@code when_hurt}, {@code when_attack},
 * {@code when_death_prevented}), times {@code scale}. Outside such an event it is {@code default}.
 *
 * <p>JSON: {@code { "type":"pheno:event_damage", "scale":0.2, "max":2 }} (heal a fifth of the hit,
 * at most a heart).</p>
 */
public final class EventDamageValueType implements ValueType {
    public static final String KEY = "pheno:event_damage";

    @Override public String key() { return KEY; }

    @Override public Value parse(JsonObject json) {
        double scale = GsonHelper.getAsDouble(json, "scale", 1.0);
        double fallback = GsonHelper.getAsDouble(json, "default", 0.0);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        return context -> {
            PhenoEvent.Frame frame = PhenoEvent.current();
            return frame == null ? fallback : Math.min(max, frame.amount() * scale);
        };
    }
}
