package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Scales a vanilla mechanic that resolves server-side: healing received
 * ({@code modify_healing}), damage dealt ({@code modify_damage_dealt}), and block
 * break speed ({@code modify_break_speed}). The {@code value} combines with the base
 * via {@code operation} ({@code multiply} default, or {@code add}); an optional
 * {@code condition} gates it. Applied by {@code GeneModifiers} off the heal/damage
 * events and the break-speed event.
 *
 * <p>Damage-taken scaling lives in {@code damage_modifier}; client-predicted movement
 * scalars (jump, swim speed) are intentionally not here, since they need the value on
 * the client too.</p>
 *
 * <p>JSON: {@code { "type":"townstead_origins:modifier", "modifier":"break_speed",
 * "value":1.5 }}</p>
 */
public final class ModifierGeneType implements GeneType {

    public static final String KEY = "townstead_origins:modifier";

    public enum Modifier {
        HEALING("healing"), DAMAGE_DEALT("damage_dealt"), BREAK_SPEED("break_speed"), JUMP("jump"),
        EXHAUSTION("exhaustion");

        private final String key;

        Modifier(String key) { this.key = key; }

        @Nullable
        static Modifier byKey(String raw) {
            if (raw == null) return null;
            String needle = raw.toLowerCase(Locale.ROOT);
            for (Modifier m : values()) if (m.key.equals(needle)) return m;
            return null;
        }
    }

    public enum Op { MULTIPLY, ADD }

    public record Instance(Modifier modifier, float value, Op op, @Nullable Condition condition)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }

        /** The base after this modifier (multiply scales it, add offsets it). */
        public float applyTo(float base) {
            return op == Op.ADD ? base + value : base * value;
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Modifier modifier = Modifier.byKey(GsonHelper.getAsString(json, "modifier", ""));
        if (modifier == null) return null;
        Op op = "add".equalsIgnoreCase(GsonHelper.getAsString(json, "operation", "multiply"))
                ? Op.ADD : Op.MULTIPLY;
        float value = GsonHelper.getAsFloat(json, "value", op == Op.ADD ? 0f : 1f);
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(modifier, value, op, condition);
    }
}
