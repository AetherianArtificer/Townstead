package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;

/**
 * True when the height of the {@code fluid} (a fluid tag) the entity stands in is within
 * {@code [min,max]} (Apoli's {@code fluid_height}). 0 when the entity is not in that fluid.
 *
 * <p>JSON: {@code { "type":"pheno:fluid_height", "fluid":"minecraft:water", "min":0.5 }}</p>
 */
public final class FluidHeightConditionType implements ConditionType {

    public static final String KEY = "pheno:fluid_height";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation fluidTag = DataPackLang.parseId(GsonHelper.getAsString(json, "fluid", ""));
        if (fluidTag == null) return null;
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, fluidTag);
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        return ctx -> {
            double height = ctx.entity().getFluidHeight(tag);
            return height >= min && height <= max;
        };
    }
}
