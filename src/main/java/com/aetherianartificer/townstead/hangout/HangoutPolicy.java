package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Individual attendance, social-beat matching, and recovery policy. */
public record HangoutPolicy(ResourceLocation id, int socialRadius, int venueRadius,
                            int minimumVisitTicks, int maximumVisitTicks,
                            int revisitCooldownTicks, int retryCooldownTicks,
                            int arrivalTimeoutTicks, int leaseTicks,
                            Map<String, Integer> bondWeights,
                            Map<String, Map<ResourceLocation, Double>> personalityTagWeights,
                            @Nullable Condition visitorWhen,
                            @Nullable Condition companionWhen) {
    public HangoutPolicy {
        Objects.requireNonNull(id, "id");
        bondWeights = bondWeights == null ? Map.of() : Map.copyOf(bondWeights);
        if (personalityTagWeights == null) {
            personalityTagWeights = Map.of();
        } else {
            Map<String, Map<ResourceLocation, Double>> copy = new LinkedHashMap<>();
            personalityTagWeights.forEach((personality, weights) -> {
                Map<ResourceLocation, Double> tagCopy = Map.copyOf(weights);
                if (tagCopy.values().stream().anyMatch(weight -> weight == null || weight <= 0D
                        || !Double.isFinite(weight))) {
                    throw new IllegalArgumentException("personality tag weights must be finite and positive");
                }
                copy.put(personality, tagCopy);
            });
            personalityTagWeights = Map.copyOf(copy);
        }
        if (socialRadius < 1 || venueRadius < 1) throw new IllegalArgumentException("radii must be positive");
        if (minimumVisitTicks < 20 || maximumVisitTicks < minimumVisitTicks) {
            throw new IllegalArgumentException("maximum_visit_ticks must be >= minimum_visit_ticks >= 20");
        }
        if (revisitCooldownTicks < 0 || retryCooldownTicks < 0) {
            throw new IllegalArgumentException("cooldowns must not be negative");
        }
        if (arrivalTimeoutTicks < 20 || leaseTicks <= arrivalTimeoutTicks) {
            throw new IllegalArgumentException("lease_ticks must exceed arrival_timeout_ticks >= 20");
        }
    }
}
