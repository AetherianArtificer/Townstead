package com.aetherianartificer.townstead.chronicle.scope;

/**
 * What a scope keeps and for how long. A scope is anything that remembers: a
 * person, a village, later a household or a region. It declares the numbers;
 * relevance itself is engine-computed ({@link ScopeRelevance}) so the decision
 * stays cheap enough to make per knower.
 */
public record ScopeProfile(String id, float threshold, int capacity, float dailyDecay) {

    public boolean retains(float relevance) {
        return relevance >= threshold;
    }

    public ScopeProfile withThreshold(float value) {
        return new ScopeProfile(id, value, capacity, dailyDecay);
    }
}
