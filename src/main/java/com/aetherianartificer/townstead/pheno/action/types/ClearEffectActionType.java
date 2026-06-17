package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Removes status effects from the actor (Apoli's {@code clear_effect}): a single
 * {@code effect} by id, or all of them when none is named. {@code removeAllEffects} is
 * uniform; the single-effect call takes a {@code Holder} on 1.21 and a {@code MobEffect}
 * on 1.20, so that path is version-guarded.
 *
 * <p>JSON: {@code { "type":"pheno:clear_effect", "effect":"minecraft:poison" }}</p>
 */
public final class ClearEffectActionType implements ActionType {

    public static final String KEY = "pheno:clear_effect";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation effectId = json.has("effect")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "effect", ""))
                : null;
        return ctx -> {
            if (effectId == null) {
                ctx.entity().removeAllEffects();
                return;
            }
            //? if >=1.21 {
            BuiltInRegistries.MOB_EFFECT
                    .getHolder(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.MOB_EFFECT, effectId))
                    .ifPresent(holder -> ctx.entity().removeEffect(holder));
            //?} else {
            /*net.minecraft.world.effect.MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
            if (effect != null) ctx.entity().removeEffect(effect);
            *///?}
        };
    }
}
