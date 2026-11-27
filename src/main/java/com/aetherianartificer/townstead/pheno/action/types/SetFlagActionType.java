package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.state.TimedFlags;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Raises a short-lived flag on the focus for {@code duration} ticks, read back by the
 * {@code pheno:flag} condition. A duration of 0 lowers it.
 *
 * <p>JSON: {@code { "type":"pheno:set_flag", "flag":"my_pack:plunging", "duration":200 }}</p>
 */
public final class SetFlagActionType implements ActionType {

    public static final String KEY = "pheno:set_flag";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation flag = DataPackLang.parseId(GsonHelper.getAsString(json, "flag", ""));
        if (flag == null) return null;
        int duration = Math.max(0, GsonHelper.getAsInt(json, "duration", 0));
        return ctx -> {
            if (!ctx.level().isClientSide()) TimedFlags.set(ctx.entity(), flag, duration);
        };
    }
}
