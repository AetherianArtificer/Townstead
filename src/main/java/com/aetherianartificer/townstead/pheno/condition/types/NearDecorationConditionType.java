package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.decoration.DecorationInstance;
import com.aetherianartificer.townstead.decoration.DecorationSavedData;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * True when a recognised decoration stands within {@code radius} of the entity. With {@code decoration}
 * it must be that definition; without it any decoration counts. Lets a pack script "curl up by the
 * hearth" without Java.
 * <pre>{ "type": "pheno:near_decoration", "decoration": "townstead:hearth", "radius": 4 }</pre>
 */
public final class NearDecorationConditionType implements ConditionType {
    public static final String KEY = "pheno:near_decoration";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation wanted = json.has("decoration")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "decoration", "")) : null;
        if (json.has("decoration") && wanted == null) return null;
        int radius = Math.max(1, Math.min(32, GsonHelper.getAsInt(json, "radius", 4)));
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level) || ctx.pos() == null) return false;
            for (DecorationInstance instance : DecorationSavedData.get(level).within(ctx.pos(), radius)) {
                if (wanted == null || instance.decorationId().equals(wanted)) return true;
            }
            return false;
        };
    }
}
