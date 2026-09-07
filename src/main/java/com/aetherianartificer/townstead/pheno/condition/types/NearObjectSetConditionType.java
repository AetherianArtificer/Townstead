package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.objectset.ObjectSetInstance;
import com.aetherianartificer.townstead.objectset.ObjectSetSavedData;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * True when a recognised object set stands within {@code radius} of the entity. With {@code set}
 * the set must be that definition; without it any set counts. Lets a pack script "curl up by the
 * hearth" without Java.
 * <pre>{ "type": "pheno:near_object_set", "set": "townstead:hearth", "radius": 4 }</pre>
 */
public final class NearObjectSetConditionType implements ConditionType {
    public static final String KEY = "pheno:near_object_set";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation wanted = json.has("set") ? DataPackLang.parseId(GsonHelper.getAsString(json, "set", "")) : null;
        if (json.has("set") && wanted == null) return null;
        int radius = Math.max(1, Math.min(32, GsonHelper.getAsInt(json, "radius", 4)));
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level) || ctx.pos() == null) return false;
            for (ObjectSetInstance instance : ObjectSetSavedData.get(level).within(ctx.pos(), radius)) {
                if (wanted == null || instance.setId().equals(wanted)) return true;
            }
            return false;
        };
    }
}
