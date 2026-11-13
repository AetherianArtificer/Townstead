package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * The current amount of a resource meter, read from the focus or, with {@code "of":"other"}, from
 * the counterpart (the caster inside an area action).
 *
 * <p>JSON: {@code { "type":"pheno:resource", "resource":"my_pack:blood", "of":"other" }}</p>
 */
public final class ResourceValueType implements ValueType {
    public static final String KEY = "pheno:resource";

    @Override public String key() { return KEY; }

    @Override
    public Value parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "resource", ""));
        if (id == null) return null;
        boolean other = "other".equalsIgnoreCase(GsonHelper.getAsString(json, "of", "self"));
        return context -> {
            LivingEntity holder = other ? context.other() : context.self();
            return holder == null ? 0 : ResourceValues.get(holder, id);
        };
    }
}
