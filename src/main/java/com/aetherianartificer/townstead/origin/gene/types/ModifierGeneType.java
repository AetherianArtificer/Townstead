package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scales a vanilla mechanic that resolves server-side. The {@code target} (alias {@code modifier}
 * for v1) selects the intercept point; each {@code modify} op combines with the live base through
 * the capability layer ({@code PhenoHooks} -> {@code Capabilities.applyToBase}), so genetics and
 * professions stack with op/priority/provenance and surface in {@code /pheno explain}. An optional
 * {@code condition} (alias {@code when}) gates it against the bearer.
 *
 * <p>Three targets carry a static discriminator keyed into the capability id so per-thing
 * modifiers do not collide: {@code status_effect_duration}/{@code status_effect_amplifier} take an
 * {@code effect}, {@code enchantment_level} takes an {@code enchantment}. A modifier without the
 * discriminator applies to all (folded together with the specific one at the hook).</p>
 *
 * <p>Client-predicted scalars (swim speed, fov, scale, falling, ...) are intentionally not here:
 * they need the value on the client too, which awaits a capability sync channel.</p>
 *
 * <p>v1: {@code { "type":"pheno:modifier", "modifier":"break_speed", "value":1.5 }}<br>
 * v2: {@code { "type":"pheno:modifier", "target":"healing",
 *      "modify":{"operation":"multiply","value":1.5}, "when":{...} }}</p>
 */
public final class ModifierGeneType implements GeneType {

    public static final String KEY = "pheno:modifier";

    public enum Modifier {
        HEALING("healing"),
        DAMAGE_DEALT("damage_dealt"),
        BREAK_SPEED("break_speed"),
        JUMP("jump"),
        EXHAUSTION("exhaustion"),
        XP_GAIN("xp_gain"),
        FOOD("food"),
        PROJECTILE_DAMAGE("projectile_damage"),
        BREEDING_COOLDOWN("breeding_cooldown"),
        STATUS_EFFECT_DURATION("status_effect_duration"),
        STATUS_EFFECT_AMPLIFIER("status_effect_amplifier"),
        ENCHANTMENT_LEVEL("enchantment_level");

        private final String key;

        Modifier(String key) { this.key = key; }

        public String key() { return key; }

        /** Targets that key in an {@code effect} discriminator. */
        public boolean usesEffect() {
            return this == STATUS_EFFECT_DURATION || this == STATUS_EFFECT_AMPLIFIER;
        }

        /** Targets that key in an {@code enchantment} discriminator. */
        public boolean usesEnchantment() {
            return this == ENCHANTMENT_LEVEL;
        }

        @Nullable
        public static Modifier byKey(String raw) {
            if (raw == null) return null;
            String needle = raw.toLowerCase(Locale.ROOT);
            for (Modifier m : values()) if (m.key.equals(needle)) return m;
            return null;
        }
    }

    public enum Op { MULTIPLY, ADD, SET, MIN, MAX }

    /** One operation in a modifier's {@code modify} list. */
    public record Mod(Op op, float value) {}

    public record Instance(Modifier modifier,
                           @Nullable ResourceLocation discriminator,
                           List<Mod> mods,
                           @Nullable Condition condition)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        String rawTarget = json.has("target")
                ? GsonHelper.getAsString(json, "target", "")
                : GsonHelper.getAsString(json, "modifier", "");
        Modifier modifier = Modifier.byKey(rawTarget);
        if (modifier == null) return null;

        ResourceLocation discriminator = null;
        if (modifier.usesEffect() && json.has("effect")) {
            discriminator = DataPackLang.parseId(GsonHelper.getAsString(json, "effect", ""));
        } else if (modifier.usesEnchantment() && json.has("enchantment")) {
            discriminator = DataPackLang.parseId(GsonHelper.getAsString(json, "enchantment", ""));
        }

        List<Mod> mods = new ArrayList<>();
        if (json.has("modify")) {
            JsonElement m = json.get("modify");
            if (m.isJsonArray()) {
                JsonArray arr = m.getAsJsonArray();
                for (JsonElement e : arr) if (e.isJsonObject()) mods.add(parseMod(e.getAsJsonObject()));
            } else if (m.isJsonObject()) {
                mods.add(parseMod(m.getAsJsonObject()));
            }
        } else {
            mods.add(parseMod(json));
        }
        if (mods.isEmpty()) return null;

        Condition condition = null;
        if (json.has("condition")) condition = Conditions.parse(json.get("condition"));
        else if (json.has("when")) condition = Conditions.parse(json.get("when"));

        return new Instance(modifier, discriminator, List.copyOf(mods), condition);
    }

    private static Mod parseMod(JsonObject o) {
        Op op = parseOp(GsonHelper.getAsString(o, "operation", "multiply"));
        float def = op == Op.ADD ? 0f : 1f;
        return new Mod(op, GsonHelper.getAsFloat(o, "value", def));
    }

    private static Op parseOp(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "add" -> Op.ADD;
            case "set", "replace" -> Op.SET;
            case "min" -> Op.MIN;
            case "max" -> Op.MAX;
            default -> Op.MULTIPLY;
        };
    }
}
