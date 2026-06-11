package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiPredicate;

/**
 * A config-less bi-entity condition backed by a predicate over the actor/target pair
 * (the bi-entity analogue of {@code StateConditionType}). Used to register the simple
 * relationship checks (attacker, can-see, riding, ...) inline.
 */
public final class SimpleBiConditionType implements BiEntityConditionType {

    private final String key;
    private final BiPredicate<LivingEntity, LivingEntity> predicate;

    public SimpleBiConditionType(String key, BiPredicate<LivingEntity, LivingEntity> predicate) {
        this.key = key;
        this.predicate = predicate;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        return predicate::test;
    }
}
