package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A race's aging/lifespan rate as a multiplier on the normal pace (1.0 = human;
 * &lt;1 ages slower / longer-lived, &gt;1 ages faster). Display-only for now; the
 * {@code multiplier} feeds the deferred life-stages/aging system.
 *
 * <p>JSON: {@code { "type":"townstead_origins:lifespan", "multiplier":1.0 }}</p>
 */
public final class LifespanGeneType implements GeneType {

    public static final String KEY = "townstead_origins:lifespan";

    public record Instance(float multiplier) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        float multiplier = GsonHelper.getAsFloat(json, "multiplier", 1.0f);
        return new Instance(Math.max(0.01f, multiplier));
        // TODO(effects): scale per-life-stage day counts by this multiplier.
    }
}
