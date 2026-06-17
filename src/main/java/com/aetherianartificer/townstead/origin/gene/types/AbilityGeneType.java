package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.ability.Ability;
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
 * Grants an innate {@link Ability} (climbing, fire immunity, night vision, …).
 * {@code mode} is {@code passive} (always on, optionally gated by a {@link Condition})
 * or {@code toggle} (the player flips it on/off with an Origin Ability key — a toggle
 * binds to a {@code slot}, shared with active abilities). Applied each tick by
 * {@code GeneAbilityTicker}.
 *
 * <p>JSON: {@code { "type":"pheno:ability", "ability":"night_vision",
 * "mode":"toggle", "slot":1 }}</p>
 */
public final class AbilityGeneType implements GeneType {

    public static final String KEY = "pheno:ability";

    public enum Mode { PASSIVE, TOGGLE }

    public record Instance(Ability ability, @Nullable Condition condition, Mode mode, int slot)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.ability(ability.key(), mode.ordinal(), slot); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Ability ability = Ability.byKey(GsonHelper.getAsString(json, "ability", ""));
        if (ability == null) return null;
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        Mode mode = "toggle".equalsIgnoreCase(GsonHelper.getAsString(json, "mode", "passive"))
                ? Mode.TOGGLE : Mode.PASSIVE;
        int slot = GsonHelper.getAsInt(json, "slot", 0);
        return new Instance(ability, condition, mode, slot);
    }
}
