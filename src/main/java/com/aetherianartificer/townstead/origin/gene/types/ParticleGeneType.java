package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Emits ambient particles around the entity (a smoldering, glowing or sparkling
 * race), optionally only while a {@link Condition} holds. Server-applied via
 * {@code sendParticles}, so it shows on every viewer. Simple particles only.
 *
 * <p>JSON: {@code { "type":"pheno:particle", "particle":"minecraft:flame",
 * "count":2, "spread":0.4, "speed":0.0, "y_offset":0.6 }}</p>
 *
 * <p>{@code spread} is the emission radius as a fraction of the entity's bounding box
 * (width for x/z, height for y); {@code speed} is the per-particle velocity;
 * {@code y_offset} is the emission height as a fraction of the entity's height. The
 * defaults reproduce the original fixed intensity.</p>
 */
public final class ParticleGeneType implements GeneType {

    public static final String KEY = "pheno:particle";

    public record Instance(ResourceLocation particle, int count, float spread, float speed, float yOffset,
                           @Nullable Condition condition) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() {
            return GeneDisplay.particle(particle, count, spread, speed, yOffset);
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        ResourceLocation particle = DataPackLang.parseId(GsonHelper.getAsString(json, "particle", ""));
        if (particle == null) return null;
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
        float spread = Math.max(0f, GsonHelper.getAsFloat(json, "spread", 0.4f));
        float speed = Math.max(0f, GsonHelper.getAsFloat(json, "speed", 0f));
        float yOffset = GsonHelper.getAsFloat(json, "y_offset", 0.6f);
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(particle, count, spread, speed, yOffset, condition);
    }
}
