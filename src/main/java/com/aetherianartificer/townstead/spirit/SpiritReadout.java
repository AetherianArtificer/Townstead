package com.aetherianartificer.townstead.spirit;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Structural description of the current village identity. Carries only the
 * pieces needed to (a) render a player-facing line via {@link #asComponent()}
 * and (b) detect a meaningful change between two readouts via
 * {@link #isStructuralChange(SpiritReadout)} so the server can fire a
 * tier-up celebration only on real transitions.
 *
 * Tier semantics depend on {@link Classification}:
 * <ul>
 *   <li>{@code SETTLEMENT} — tier 0; no spirit has crossed the first threshold.</li>
 *   <li>{@code SINGLE} — tier 1..5 of the primary spirit; readout is the
 *       descriptive per-tier name ("Fishing Spot", "Bastion", etc.).</li>
 *   <li>{@code BLEND} — tier 1..5 (max of the two spirits' tiers, used for
 *       effect intensity); readout is the flat pair name ("Saltmarsh",
 *       "Privateer Port", etc.) regardless of tier.</li>
 *   <li>{@code MIXED} — tier is the spread level 1..4
 *       (Crossroads / Metropolis / Cosmopolis / The Convergence).</li>
 * </ul>
 *
 * Blend pair ordering is canonicalized by the aggregator (lower registry
 * index first) so lang keys are deterministic.
 */
public record SpiritReadout(
        Classification classification,
        int tierIndex,
        @Nullable String primarySpiritId,
        @Nullable String secondarySpiritId
) {
    public enum Classification { SETTLEMENT, SINGLE, BLEND, MIXED }

    public Component asComponent() {
        return Component.translatable(translationKey());
    }

    /**
     * Raw translation key for this readout (no args). Useful for serializing
     * the readout across the network without shipping a full Component.
     */
    public String translationKey() {
        return switch (classification) {
            case SETTLEMENT -> "townstead.spirit.readout.settlement";
            case SINGLE -> singleTierKey(primarySpiritId, tierIndex);
            case BLEND -> blendKey(primarySpiritId, secondarySpiritId);
            case MIXED -> mixedKey(tierIndex);
        };
    }

    public boolean isStructuralChange(@Nullable SpiritReadout other) {
        if (other == null) return true;
        if (classification != other.classification) return true;
        if (tierIndex != other.tierIndex) return true;
        if (!Objects.equals(primarySpiritId, other.primarySpiritId)) return true;
        if (!Objects.equals(secondarySpiritId, other.secondarySpiritId)) return true;
        return false;
    }

    private static String singleTierKey(@Nullable String spiritId, int tier) {
        if (spiritId == null || tier < 1 || tier > 5) {
            return "townstead.spirit.readout.settlement";
        }
        return "townstead.spirit.tier." + spiritId + "." + tier;
    }

    private static String blendKey(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return "townstead.spirit.readout.settlement";
        return "townstead.spirit.blend." + a + "." + b;
    }

    private static String mixedKey(int spread) {
        return switch (spread) {
            case 4 -> "townstead.spirit.mixed.convergence";
            case 3 -> "townstead.spirit.mixed.cosmopolis";
            case 2 -> "townstead.spirit.mixed.metropolis";
            default -> "townstead.spirit.mixed.crossroads";
        };
    }
}
