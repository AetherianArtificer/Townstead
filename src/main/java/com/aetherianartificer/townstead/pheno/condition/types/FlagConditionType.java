package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.state.TimedFlags;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * True while a flag raised by {@code pheno:set_flag} has not run out. Server only.
 *
 * <p>JSON: {@code { "type":"pheno:flag", "flag":"my_pack:plunging" }}</p>
 */
public final class FlagConditionType implements ConditionType {

    public static final String KEY = "pheno:flag";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation flag = DataPackLang.parseId(GsonHelper.getAsString(json, "flag", ""));
        if (flag == null) return null;
        return ctx -> ctx.entity() != null && !ctx.entity().level().isClientSide()
                && TimedFlags.has(ctx.entity(), flag);
    }
}
