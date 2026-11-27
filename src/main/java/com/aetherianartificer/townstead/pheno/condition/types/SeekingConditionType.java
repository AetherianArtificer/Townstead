package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.seek.Seeks;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Whether the entity is on that seek ({@code id}), or on any seek without one.
 *
 * <p>JSON: {@code { "type":"pheno:seeking", "id":"my_pack:rounds" }}</p>
 */
public final class SeekingConditionType implements ConditionType {

    public static final String KEY = "pheno:seeking";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = json.has("id") ? DataPackLang.parseId(GsonHelper.getAsString(json, "id", "")) : null;
        if (json.has("id") && id == null) return null;
        return ctx -> ctx.entity() != null && Seeks.seeking(ctx.entity(), id);
    }
}
