package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Makes an outline glow, optionally only while a {@link Condition} holds.
 *
 * <p>Without {@code on} the holder itself glows. Applied server-side via the forced-glowing
 * flag, which syncs to all viewers; the outline colour follows vanilla team colouring (custom
 * per-gene colour is not yet supported). Maps Apoli's {@code self_glow}.</p>
 *
 * <p>With {@code on} the selected entities glow for the holder alone: the server resolves the
 * selector and sends the holding player the ids, and only that client draws the outline, so
 * nothing is set on the glowing entities. Maps Apoli's {@code entity_glow}.</p>
 *
 * <p>JSON: {@code { "type":"pheno:glow",
 * "condition":{ "type":"pheno:in_fluid", "fluid":"minecraft:water" } }} or
 * {@code { "type":"pheno:glow", "on":{ "radius":8, "where":{ "type":"pheno:hostile" } } }}</p>
 */
public final class GlowGeneType implements GeneType {

    public static final String KEY = "pheno:glow";

    public record Instance(@Nullable Condition condition, @Nullable Selector on) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        Selector on = null;
        if (json.has("on")) {
            on = Selectors.parse(json.get("on"));
            if (on == null) return null;
        }
        return new Instance(condition, on);
    }
}
