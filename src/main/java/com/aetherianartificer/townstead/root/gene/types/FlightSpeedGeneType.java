package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Scales creative-style flight speed for the bearer: {@code multiplier} times vanilla's
 * 0.05 base (sprint still doubles it). Applied by {@code GeneAbilityTicker} to the player's
 * flight ability, which syncs to the client, so the client's own movement code does the
 * slowing. That matters: creative flight is client-paced, so a server-side velocity nudge
 * (an {@code action_over_time} running {@code pheno:add_velocity}) is overwritten by the
 * next movement packet and only ever stutters. Speed is a value the client reads, not a
 * force the server pushes.
 *
 * <p>Independent of what granted the flight, so it slows a creative-mode or op player too,
 * not only a bearer of {@code pheno:ability creative_flight}. Multiple expressed copies
 * multiply together. A {@code multiplier} of 0 pins the bearer in place while flying.
 * Elytra is a different mechanism and is not affected.</p>
 *
 * <p>JSON: {@code { "type":"pheno:flight_speed", "multiplier":0.5,
 * "condition":{ "type":"pheno:environment", "exposure":"sky" } }}</p>
 */
public final class FlightSpeedGeneType implements GeneType {

    public static final String KEY = "pheno:flight_speed";

    public record Instance(float multiplier, @Nullable Condition condition) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        float multiplier = GsonHelper.getAsFloat(json, "multiplier", 1f);
        multiplier = Math.max(0f, Math.min(10f, multiplier));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(multiplier, condition);
    }
}
