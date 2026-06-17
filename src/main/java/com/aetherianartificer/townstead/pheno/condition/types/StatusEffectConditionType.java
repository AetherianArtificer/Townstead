package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
//?} else {
/*import net.minecraft.world.effect.MobEffect;
*///?}

/**
 * True when the entity has the named status effect (optionally at or above a
 * minimum amplifier).
 *
 * <p>JSON: {@code { "type":"pheno:status_effect", "effect":"minecraft:poison" }}</p>
 */
public final class StatusEffectConditionType implements ConditionType {

    public static final String KEY = "pheno:status_effect";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "effect", ""));
        if (id == null) return null;
        int minAmplifier = GsonHelper.getAsInt(json, "min_amplifier", 0);
        //? if neoforge {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
        if (effect == null) return null;
        return ctx -> {
            var instance = ctx.entity().getEffect(effect);
            return instance != null && instance.getAmplifier() >= minAmplifier;
        };
        //?} else {
        /*MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) return null;
        return ctx -> {
            var instance = ctx.entity().getEffect(effect);
            return instance != null && instance.getAmplifier() >= minAmplifier;
        };
        *///?}
    }
}
