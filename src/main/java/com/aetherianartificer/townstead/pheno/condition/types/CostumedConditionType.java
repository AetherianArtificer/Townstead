package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.replace.MobReplacement;
import com.aetherianartificer.townstead.replace.MobReplacer;
import com.aetherianartificer.townstead.replace.WildCostume;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:costumed}: the bearer is the person a costumed mob shows, optionally of one
 * replacement {@code group} (such as {@code hunter}).
 *
 * <p>JSON: {@code { "type":"pheno:costumed", "group":"hunter" }}</p>
 */
public final class CostumedConditionType implements ConditionType {
    public static final String KEY = "pheno:costumed";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String group = json.has("group") ? GsonHelper.getAsString(json, "group") : null;
        return ctx -> {
            if (ctx.entity() == null || WildCostume.wearer(ctx.entity()) == null) return false;
            if (group == null) return true;
            MobReplacement replacement = MobReplacer.replacementOf(ctx.entity());
            return replacement != null && group.equals(replacement.group());
        };
    }
}
