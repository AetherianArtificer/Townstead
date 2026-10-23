package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.replace.WildCostume;
import com.aetherianartificer.townstead.root.disposition.Disposition;
import com.aetherianartificer.townstead.root.disposition.Dispositions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * {@code pheno:regards}: how the bearer regards the other party ({@code hostile}, {@code neutral}
 * or {@code friendly}). For the person a costumed mob shows, the mob's own stance counts.
 *
 * <p>JSON: {@code { "type":"pheno:regards", "is":"friendly" }}</p>
 */
public final class RegardsConditionType implements ConditionType {
    public static final String KEY = "pheno:regards";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Disposition wanted;
        try {
            wanted = Disposition.valueOf(GsonHelper.getAsString(json, "is").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return ctx -> {
            if (ctx.entity() == null || ctx.other() == null) return false;
            LivingEntity self = WildCostume.wearer(ctx.entity());
            return Dispositions.between(self != null ? self : ctx.entity(), ctx.other()) == wanted;
        };
    }
}
