package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;

/**
 * True when the entity has at least {@code min_count} active effects belonging to a
 * mob-effect {@code tag} (Apugli's {@code status_effect_tag}).
 *
 * <p>JSON: {@code { "type":"pheno:status_effect_tag", "tag":"c:harmful" }}</p>
 */
public final class StatusEffectTagConditionType implements ConditionType {

    public static final String KEY = "pheno:status_effect_tag";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
        if (id == null) return null;
        TagKey<MobEffect> tag = TagKey.create(Registries.MOB_EFFECT, id);
        int minCount = GsonHelper.getAsInt(json, "min_count", 1);
        return ctx -> {
            int count = 0;
            for (var instance : ctx.entity().getActiveEffects()) {
                //? if neoforge {
                if (instance.getEffect().is(tag)) count++;
                //?} else {
                /*if (BuiltInRegistries.MOB_EFFECT.wrapAsHolder(instance.getEffect()).is(tag)) count++;
                *///?}
            }
            return count >= minCount;
        };
    }
}
