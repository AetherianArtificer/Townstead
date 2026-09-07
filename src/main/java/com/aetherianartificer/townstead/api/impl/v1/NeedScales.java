package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.api.v1.model.NeedLevel;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one place a raw need reading becomes a {@link NeedLevel}: scale, neutral point, band and
 * crisis flag per need id. Both the live snapshot and the resident register go through here so
 * a loaded and an unloaded villager are judged on the same edges.
 */
public final class NeedScales {
    public static final List<String> IDS = List.of(
            NeedsSnapshot.HUNGER, NeedsSnapshot.THIRST, NeedsSnapshot.ENERGY, NeedsSnapshot.TEMPERATURE);

    /** Neutral points: the floor of each need's top band. */
    static final int HUNGER_NEUTRAL = HungerData.BREAKFAST_THRESHOLD;
    static final int THIRST_NEUTRAL = ThirstData.BREAKFAST_THRESHOLD;
    static final int ENERGY_NEUTRAL = FatigueData.MAX_FATIGUE - 4 + 1;
    static final int TEMPERATURE_NEUTRAL = TemperatureData.tenths(37f);

    private NeedScales() {}

    public static NeedLevel hunger(int hunger, boolean enabled) {
        HungerData.HungerState state = HungerData.getState(hunger);
        return new NeedLevel(NeedsSnapshot.HUNGER, hunger, 0, HungerData.MAX_HUNGER, HUNGER_NEUTRAL,
                lower(state.name()), state == HungerData.HungerState.STARVING, enabled);
    }

    public static NeedLevel thirst(int thirst, boolean enabled) {
        ThirstData.ThirstState state = ThirstData.getState(thirst);
        return new NeedLevel(NeedsSnapshot.THIRST, thirst, 0, ThirstData.MAX_THIRST, THIRST_NEUTRAL,
                lower(state.name()), state == ThirstData.ThirstState.DEHYDRATED, enabled);
    }

    /** Takes fatigue, publishes energy. */
    public static NeedLevel energy(int fatigue, boolean enabled) {
        FatigueData.FatigueState state = FatigueData.getState(fatigue);
        int energy = Math.max(0, Math.min(FatigueData.MAX_FATIGUE, FatigueData.MAX_FATIGUE - fatigue));
        return new NeedLevel(NeedsSnapshot.ENERGY, energy, 0, FatigueData.MAX_FATIGUE, ENERGY_NEUTRAL,
                lower(state.name()), state == FatigueData.FatigueState.EXHAUSTED, enabled);
    }

    public static NeedLevel temperature(int bodyTenths, int tierIndex, boolean crisis, boolean enabled) {
        TemperatureData.Tier[] tiers = TemperatureData.Tier.values();
        TemperatureData.Tier tier = tierIndex >= 0 && tierIndex < tiers.length
                ? tiers[tierIndex] : TemperatureData.Tier.COMFORTABLE;
        int value = bodyTenths == Integer.MIN_VALUE ? TEMPERATURE_NEUTRAL : bodyTenths;
        return new NeedLevel(NeedsSnapshot.TEMPERATURE, value, TemperatureData.MIN_BODY_TENTHS,
                TemperatureData.MAX_BODY_TENTHS, TEMPERATURE_NEUTRAL, lower(tier.name()), crisis, enabled);
    }

    public static Map<String, NeedLevel> levels(TownsteadVillager.Needs needs) {
        Map<String, NeedLevel> out = new LinkedHashMap<>();
        out.put(NeedsSnapshot.HUNGER, hunger(needs.hunger(), hungerEnabled()));
        out.put(NeedsSnapshot.THIRST, thirst(needs.thirst(), thirstEnabled()));
        out.put(NeedsSnapshot.ENERGY, energy(needs.fatigue(), fatigueEnabled()));
        out.put(NeedsSnapshot.TEMPERATURE, temperature(needs.bodyTempTenths(), needs.thermalTier(),
                needs.thermalCrisis(), temperatureEnabled() && needs.hasBodyTemp()));
        return out;
    }

    /** Rebuilds levels from the compact per-need ints the resident register stores. */
    public static Map<String, NeedLevel> levels(int hunger, int thirst, int fatigue, int bodyTenths, int thermalTier,
                                         boolean thermalCrisis) {
        Map<String, NeedLevel> out = new LinkedHashMap<>();
        out.put(NeedsSnapshot.HUNGER, hunger(hunger, hungerEnabled()));
        out.put(NeedsSnapshot.THIRST, thirst(thirst, thirstEnabled()));
        out.put(NeedsSnapshot.ENERGY, energy(fatigue, fatigueEnabled()));
        out.put(NeedsSnapshot.TEMPERATURE, temperature(bodyTenths, thermalTier, thermalCrisis,
                temperatureEnabled() && bodyTenths != Integer.MIN_VALUE));
        return out;
    }

    public static boolean hungerEnabled() {
        try {
            return TownsteadConfig.ENABLE_VILLAGER_HUNGER.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean thirstEnabled() {
        try {
            return TownsteadConfig.ENABLE_VILLAGER_THIRST.get() && ThirstBridgeResolver.isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fatigueEnabled() {
        try {
            return TownsteadConfig.ENABLE_VILLAGER_FATIGUE.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean temperatureEnabled() {
        return TownsteadConfig.isVillagerTemperatureEnabled();
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
