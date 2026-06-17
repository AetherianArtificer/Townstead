package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;

import java.util.function.Predicate;

/**
 * A config-less boolean state condition (in rain, on fire, sneaking, …). One
 * instance is registered per state with its own key and live-state predicate, so
 * the simple states don't each need their own class.
 */
public final class StateConditionType implements ConditionType {

    private final String key;
    private final Predicate<ConditionContext> test;

    public StateConditionType(String key, Predicate<ConditionContext> test) {
        this.key = key;
        this.test = test;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Condition parse(JsonObject json) {
        return test::test;
    }
}
