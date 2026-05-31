package com.aetherianartificer.townstead.origin.trait.effect.types;

import com.aetherianartificer.townstead.origin.trait.effect.TraitEffectType;
import com.google.gson.JsonElement;

/**
 * {@code life.immortal} — a boolean capability. A villager carrying any trait with
 * {@code life.immortal: true} is treated as immortal (life stage pinned, vanilla age
 * frozen) by {@code TraitEffects.isImmortal}, queried at the server-side age sites.
 * Separate from the potion's {@code Life.immortal} flag, which is non-heritable.
 */
public final class SetImmortalEffectType implements TraitEffectType {

    public static final String KEY = "life.immortal";

    @Override
    public String key() { return KEY; }

    @Override
    public boolean validate(JsonElement config) {
        return config.isJsonPrimitive() && config.getAsJsonPrimitive().isBoolean();
    }
}
