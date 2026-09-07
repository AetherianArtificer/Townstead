package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A village's spirit: points per spirit from its completed buildings, and the readout Townstead
 * derives from them. {@link #classification} is one of {@code settlement}, {@code single},
 * {@code blend}, {@code mixed}; new values may appear.
 */
public record SpiritSnapshot(
        VillageId village,
        Map<String, Integer> perSpirit,
        int total,
        int contributingBuildings,
        String classification,
        int tierIndex,
        Optional<String> primarySpiritId,
        Optional<String> secondarySpiritId,
        List<Integer> tierThresholds,
        Component label
) {
    public SpiritSnapshot {
        perSpirit = perSpirit == null ? Map.of() : Map.copyOf(perSpirit);
        primarySpiritId = primarySpiritId == null ? Optional.empty() : primarySpiritId;
        secondarySpiritId = secondarySpiritId == null ? Optional.empty() : secondarySpiritId;
        tierThresholds = tierThresholds == null ? List.of() : List.copyOf(tierThresholds);
    }

    public int pointsFor(String spiritId) {
        return perSpirit.getOrDefault(spiritId, 0);
    }

    public double shareOf(String spiritId) {
        return total <= 0 ? 0.0 : pointsFor(spiritId) / (double) total;
    }

    /** True when classification, tier, primary or secondary differ from {@code other}. */
    public boolean isStructuralChange(SpiritSnapshot other) {
        if (other == null) return true;
        return !classification.equals(other.classification)
                || tierIndex != other.tierIndex
                || !primarySpiritId.equals(other.primarySpiritId)
                || !secondarySpiritId.equals(other.secondarySpiritId);
    }
}
