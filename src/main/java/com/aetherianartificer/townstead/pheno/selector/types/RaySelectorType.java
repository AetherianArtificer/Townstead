package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorType;
import com.aetherianartificer.townstead.pheno.selector.spatial.RayCast;
import com.google.gson.JsonObject;

/**
 * The entity form of {@code pheno:ray}: the entity (or all, when piercing) a ray strikes, so an
 * entity action with {@code on: { type: pheno:ray, ... }} acts on what the caster is aiming at
 * (Apoli/Apugli {@code raycast}). The same id is a block selector in a block action.
 */
public final class RaySelectorType implements SelectorType {

    public static final String KEY = "pheno:ray";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Selector parse(JsonObject json) {
        RayCast ray = RayCast.parse(json);
        return ray::entityHits;
    }
}
