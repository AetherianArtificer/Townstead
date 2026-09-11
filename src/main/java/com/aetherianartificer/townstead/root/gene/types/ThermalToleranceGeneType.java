package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;

/**
 * Where a root's body rests and how wide its comfort is. {@code neutral} is the resting body
 * temperature in Celsius, {@code band} the half-width of the comfortable range; the temperature
 * tiers scale off the band. {@code cold} and {@code heat} scale the two halves of the ambient pull
 * (0 immune, 1 baseline, 2 doubled). {@code "climate": "any"} switches the need off entirely, the
 * {@code diet: none} precedent.
 *
 * <p>JSON: {@code { "type":"pheno:thermal_tolerance", "neutral":37.0, "band":0.5, "cold":1.0, "heat":1.0 }}
 * or {@code { "type":"pheno:thermal_tolerance", "climate":"any" }}.</p>
 */
public final class ThermalToleranceGeneType implements GeneType {

    public static final String KEY = "pheno:thermal_tolerance";
    public static final String CLIMATE_ANY = "any";

    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(float neutral, float band, float cold, float heat, boolean climateAny) implements GeneInstance {
        @Override public String typeKey() { return KEY; }

        @Override public GeneDisplay display() {
            return climateAny ? GeneDisplay.suppressNeed(List.of("temperature")) : GeneDisplay.PRESENCE;
        }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        boolean any = CLIMATE_ANY.equalsIgnoreCase(GsonHelper.getAsString(json, "climate", ""));
        float neutral = GsonHelper.getAsFloat(json, "neutral", TemperatureData.DEFAULT_NEUTRAL);
        float band = GsonHelper.getAsFloat(json, "band", TemperatureData.DEFAULT_BAND);
        float cold = any ? 0f : clamp(GsonHelper.getAsFloat(json, "cold", 1f));
        float heat = any ? 0f : clamp(GsonHelper.getAsFloat(json, "heat", 1f));
        return new Instance(neutral, Math.max(0.1f, band), cold, heat, any);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(2f, v));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
