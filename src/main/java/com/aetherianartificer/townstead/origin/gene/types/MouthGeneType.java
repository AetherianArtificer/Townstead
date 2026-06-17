package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A mouth set for a custom-faced rig: a variant gene whose variants are the selectable mouth styles.
 * Each variant carries its sprite-strip {@code texture} (frames {@code neutral, happy, unhappy};
 * the renderer picks by MCA mood). Baked colour for now (no mouth tint gene yet). Rolled at spawn,
 * editable via the variant picker, inherited like any variant gene.
 *
 * <p>JSON variant: {@code { "texture":"ns:textures/face/mouth/grin.png" }}</p>
 */
public final class MouthGeneType implements GeneType {

    public static final String KEY = "townstead_origins:mouth";

    public record Instance(String texture) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance(GsonHelper.getAsString(json, "texture", ""));
    }
}
