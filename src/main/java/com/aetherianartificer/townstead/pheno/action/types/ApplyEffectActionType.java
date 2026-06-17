package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffectInstance;
//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
//?} else {
/*import net.minecraft.world.effect.MobEffect;
*///?}

/**
 * Applies a status effect to the actor.
 *
 * <p>JSON: {@code { "type":"pheno:apply_effect", "effect":"minecraft:speed",
 * "duration":200, "amplifier":1 }}</p>
 */
public final class ApplyEffectActionType implements ActionType {

    public static final String KEY = "pheno:apply_effect";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "effect", ""));
        if (id == null) return null;
        int duration = GsonHelper.getAsInt(json, "duration", 200);
        int amplifier = GsonHelper.getAsInt(json, "amplifier", 0);
        //? if neoforge {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
        if (effect == null) return null;
        return ctx -> ctx.entity().addEffect(new MobEffectInstance(effect, duration, amplifier));
        //?} else {
        /*MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) return null;
        return ctx -> ctx.entity().addEffect(new MobEffectInstance(effect, duration, amplifier));
        *///?}
    }
}
