package com.aetherianartificer.townstead.client.animation.cem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Select inputs once at pack load; render evaluations only compute the selected values. */
final class CemInputPlan<C> {
    private record Input<C>(int slot, ToDoubleFunction<C> value) {}

    private final List<Input<C>> inputs;

    private CemInputPlan(List<Input<C>> inputs) {
        this.inputs = List.copyOf(inputs);
    }

    void seed(C context, CemVariableStore variables) {
        for (Input<C> input : inputs) variables.seed(input.slot(), input.value().applyAsDouble(context));
    }

    static final class Builder<C> {
        private final CemVariableStore.Layout layout;
        private final List<Input<C>> inputs = new ArrayList<>();

        Builder(CemVariableStore.Layout layout) {
            this.layout = layout;
        }

        void add(String key, ToDoubleFunction<C> value) {
            if (layout.references(key)) inputs.add(new Input<>(layout.slot(key), value));
        }

        CemInputPlan<C> build() {
            return new CemInputPlan<>(inputs);
        }
    }
}
