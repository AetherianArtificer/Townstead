package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the ambient-temperature backend from the config preference, the way
 * {@code ThirstBridgeResolver} does for thirst. Unlike thirst there is always a backend: the
 * built-in fallback answers when no temperature mod is installed. Every installed mod bridge is
 * also kept in a list, because block and item opinions come from all of them.
 */
public final class TemperatureBridgeResolver {
    private static final List<AmbientTemperatureBridge> MODDED = List.of(
            LsoTemperatureBridge.INSTANCE,
            ColdSweatTemperatureBridge.INSTANCE,
            ToughAsNailsTemperatureBridge.INSTANCE);

    private static volatile String resolvedPreference;
    private static volatile AmbientTemperatureBridge cachedBridge = BuiltinTemperatureBridge.INSTANCE;
    private static volatile List<AmbientTemperatureBridge> installed;

    private TemperatureBridgeResolver() {}

    /** Reflection-free presence probe, safe while the config spec is being built. */
    public static boolean anyTemperatureModLoaded() {
        return ModCompat.isLoaded("legendarysurvivaloverhaul")
                || ModCompat.isLoaded("cold_sweat")
                || ModCompat.isLoaded("toughasnails");
    }

    /** The backend that drives the ambient reading. */
    public static AmbientTemperatureBridge get() {
        String preference = TownsteadConfig.preferredTemperatureBackend();
        if (!preference.equals(resolvedPreference)) resolve(preference);
        return cachedBridge;
    }

    /** Every active mod backend, in preference order, for block and item opinions. */
    public static List<AmbientTemperatureBridge> installed() {
        List<AmbientTemperatureBridge> list = installed;
        if (list == null) {
            list = new ArrayList<>();
            for (AmbientTemperatureBridge bridge : MODDED) if (bridge.isActive()) list.add(bridge);
            installed = List.copyOf(list);
        }
        return installed;
    }

    private static synchronized void resolve(String preference) {
        if (preference.equals(resolvedPreference)) return;
        AmbientTemperatureBridge selected = null;
        if (!"builtin".equals(preference)) {
            for (AmbientTemperatureBridge bridge : installed()) {
                if ("auto".equals(preference) || bridge.id().equals(preference)) {
                    selected = bridge;
                    break;
                }
            }
            if (selected == null && !"auto".equals(preference) && !installed().isEmpty()) {
                selected = installed().get(0);
            }
        }
        cachedBridge = selected == null ? BuiltinTemperatureBridge.INSTANCE : selected;
        resolvedPreference = preference;
    }
}
