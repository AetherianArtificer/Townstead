package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Innate clothing: fur, blubber, chitin, scales. {@code amount} is a Celsius offset on the body's
 * target, positive for warmth. Positive insulation helps in the cold and, unless {@code sheds},
 * hurts in the heat, the way a fur coat does in a desert.
 *
 * <p>JSON: {@code { "type":"pheno:insulation", "amount":0.5, "sheds":false }}.</p>
 */
public final class InsulationGeneType implements GeneType {

    public static final String KEY = "pheno:insulation";

    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(float amount, boolean sheds) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        float amount = GsonHelper.getAsFloat(json, "amount", 0f);
        amount = Math.max(-TemperatureData.CLOTHING_CLAMP, Math.min(TemperatureData.CLOTHING_CLAMP, amount));
        return new Instance(amount, GsonHelper.getAsBoolean(json, "sheds", false));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
