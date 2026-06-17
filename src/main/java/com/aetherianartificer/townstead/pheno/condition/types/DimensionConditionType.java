package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity is in the named dimension.
 *
 * <p>JSON: {@code { "type":"pheno:dimension", "dimension":"minecraft:the_nether" }}</p>
 */
public final class DimensionConditionType implements ConditionType {

    public static final String KEY = "pheno:dimension";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation dimension = DataPackLang.parseId(GsonHelper.getAsString(json, "dimension", ""));
        if (dimension == null) return null;
        return ctx -> ctx.level().dimension().location().equals(dimension);
    }
}
