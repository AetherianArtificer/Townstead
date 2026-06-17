package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
//?} else {
/*import net.minecraft.world.entity.ai.attributes.Attribute;
*///?}

/**
 * True when the entity's value for {@code attribute} is within {@code [min, max]}
 * (Apoli's {@code attribute}). The attribute is a {@code Holder} on 1.21 and a raw
 * {@code Attribute} on 1.20, so resolution is version-guarded.
 *
 * <p>JSON: {@code { "type":"pheno:attribute",
 * "attribute":"minecraft:generic.movement_speed", "min":0.12 }}</p>
 */
public final class AttributeConditionType implements ConditionType {

    public static final String KEY = "pheno:attribute";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "attribute", ""));
        if (id == null) return null;
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        //? if neoforge {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, id)).orElse(null);
        //?} else {
        /*Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        *///?}
        if (attribute == null) return null;
        return ctx -> {
            if (!ctx.entity().getAttributes().hasAttribute(attribute)) return false;
            double value = ctx.entity().getAttributeValue(attribute);
            return value >= min && value <= max;
        };
    }
}
