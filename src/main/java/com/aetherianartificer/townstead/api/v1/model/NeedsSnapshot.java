package com.aetherianartificer.townstead.api.v1.model;

import java.util.Map;
import java.util.Optional;

/**
 * A villager's needs. The typed fields are the classic three plus temperature; {@link #levels}
 * carries every registered need keyed by id with its range and band, and is the field that grows.
 *
 * <p>Scales differ per need and travel with the value in {@link NeedLevel}. Energy is published
 * as energy, where higher is better; Townstead stores its inverse as fatigue internally.
 */
public record NeedsSnapshot(
        int hunger,
        float saturation,
        float hungerExhaustion,
        int thirst,
        int quenched,
        float thirstExhaustion,
        int energy,
        boolean collapsed,
        boolean thirstSimulated,
        int bodyTemperatureTenths,
        int ambientTemperatureTenths,
        Map<String, NeedLevel> levels
) {
    public static final String HUNGER = "townstead:hunger";
    public static final String SATURATION = "townstead:saturation";
    public static final String THIRST = "townstead:thirst";
    public static final String QUENCHED = "townstead:quenched";
    public static final String ENERGY = "townstead:energy";
    public static final String TEMPERATURE = "townstead:temperature";

    public NeedsSnapshot {
        levels = levels == null ? Map.of() : Map.copyOf(levels);
    }

    public Optional<NeedLevel> level(String needId) {
        return Optional.ofNullable(levels.get(needId));
    }

    /** The need furthest below its neutral point, as a fraction of its range, if any is below it. */
    public Optional<String> primaryNeed() {
        String worst = null;
        double worstShortfall = 0.0;
        for (Map.Entry<String, NeedLevel> entry : levels.entrySet()) {
            double shortfall = entry.getValue().shortfall();
            if (shortfall > worstShortfall) {
                worstShortfall = shortfall;
                worst = entry.getKey();
            }
        }
        return Optional.ofNullable(worst);
    }

    /** True when any need sits in its crisis band. */
    public boolean inCrisis() {
        for (NeedLevel level : levels.values()) {
            if (level.crisis()) return true;
        }
        return false;
    }
}
