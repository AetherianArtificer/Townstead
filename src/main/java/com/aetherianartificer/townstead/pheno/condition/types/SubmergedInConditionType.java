package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;

import java.util.Locale;

/**
 * True when the entity's eyes are in the given fluid (Apoli's {@code submerged_in}).
 * {@code fluid} is {@code water} or {@code lava}, or a {@code fluid_tag} can be named.
 *
 * <p>JSON: {@code { "type":"pheno:submerged_in", "fluid":"lava" }}</p>
 */
public final class SubmergedInConditionType implements ConditionType {

    public static final String KEY = "pheno:submerged_in";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        TagKey<Fluid> tag;
        if (json.has("fluid_tag")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "fluid_tag", ""));
            if (id == null) return null;
            tag = TagKey.create(Registries.FLUID, id);
        } else {
            tag = switch (GsonHelper.getAsString(json, "fluid", "water").toLowerCase(Locale.ROOT)) {
                case "lava" -> FluidTags.LAVA;
                default -> FluidTags.WATER;
            };
        }
        return ctx -> ctx.entity().isEyeInFluid(tag);
    }
}
