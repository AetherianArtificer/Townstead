package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/** Group formation and recovery policy, separate from venues and activities. */
public record HangoutPolicy(ResourceLocation id, int minimumGroup, int maximumGroup,
                            boolean soloFallback, int inviteRadius, int venueRadius,
                            int cooldownTicks, int arrivalTimeoutTicks, int leaseTicks,
                            Map<String, Integer> bondWeights,
                            @Nullable Condition initiatorWhen,
                            @Nullable Condition companionWhen) {
    public HangoutPolicy {
        Objects.requireNonNull(id, "id");
        bondWeights = bondWeights == null ? Map.of() : Map.copyOf(bondWeights);
        if (minimumGroup < 1) throw new IllegalArgumentException("minimum_group must be positive");
        if (maximumGroup < minimumGroup) throw new IllegalArgumentException("maximum_group must be >= minimum_group");
        if (inviteRadius < 1 || venueRadius < 1) throw new IllegalArgumentException("radii must be positive");
        if (cooldownTicks < 0) throw new IllegalArgumentException("cooldown_ticks must not be negative");
        if (arrivalTimeoutTicks < 20 || leaseTicks <= arrivalTimeoutTicks) {
            throw new IllegalArgumentException("lease_ticks must exceed arrival_timeout_ticks >= 20");
        }
    }
}
