package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Scales the bearer's whole body: model, hitbox and eye height together, for players and
 * villagers alike. Several scale genes multiply. {@code reach} (default false) also scales block
 * and entity reach, and {@code speed} (default false) scales walking speed, so a giant can stride
 * and a sprite can stay quick.
 *
 * <p>On 1.21 this drives the vanilla {@code generic.scale} attribute; on 1.20.1 Townstead sizes
 * and renders the body itself. Villagers keep Townstead's door-safe size limits whatever the
 * scale: a giant villager renders large but still fits through a door.</p>
 *
 * <p>JSON: {@code { "type":"pheno:scale", "scale":1.2 }}</p>
 */
public final class ScaleGeneType implements GeneType {

    public static final String KEY = "pheno:scale";
    public static final float MIN = 0.25f, MAX = 3f;

    public record Instance(float scale, boolean reach, boolean speed) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.bodyScale(scale); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        float scale = GsonHelper.getAsFloat(json, "scale", 1f);
        if (!Float.isFinite(scale) || scale <= 0f) return null;
        return new Instance(Math.max(MIN, Math.min(MAX, scale)),
                GsonHelper.getAsBoolean(json, "reach", false),
                GsonHelper.getAsBoolean(json, "speed", false));
    }
}
