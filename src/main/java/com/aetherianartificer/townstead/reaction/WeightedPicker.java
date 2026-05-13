package com.aetherianartificer.townstead.reaction;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Single-shot weighted random pick. Skips entries whose weight is {@code
 * <= 0}. Returns {@link Optional#empty()} when no entry has positive
 * weight.
 */
public final class WeightedPicker {
    private WeightedPicker() {}

    public static <T> Optional<T> pick(List<T> entries, ToDoubleFunction<T> weight, RandomSource random) {
        if (entries == null || entries.isEmpty()) return Optional.empty();
        double total = 0.0;
        for (T entry : entries) {
            double w = weight.applyAsDouble(entry);
            if (w > 0.0) total += w;
        }
        if (total <= 0.0) return Optional.empty();
        double roll = random.nextDouble() * total;
        double accum = 0.0;
        for (T entry : entries) {
            double w = weight.applyAsDouble(entry);
            if (w <= 0.0) continue;
            accum += w;
            if (roll < accum) return Optional.of(entry);
        }
        // Fall through guard against floating-point drift: return last positive-weight entry.
        for (int i = entries.size() - 1; i >= 0; i--) {
            T entry = entries.get(i);
            if (weight.applyAsDouble(entry) > 0.0) return Optional.of(entry);
        }
        return Optional.empty();
    }
}
