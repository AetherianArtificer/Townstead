package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.ThermalConsumables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Read the live registry, including other mods and datapacks, independently of thirst. */
public final class LsoConsumables {
    private static boolean initialized;
    private static Method getConsumable;
    private LsoConsumables() {}

    public static List<ThermalConsumables.Status> effects(ItemStack stack) {
        if (!ModCompat.isLoaded("legendarysurvivaloverhaul")) return List.of();
        init();
        if (getConsumable == null) return List.of();
        try {
            Object raw = getConsumable.invoke(null, BuiltInRegistries.ITEM.getKey(stack.getItem()));
            return raw instanceof List<?> list ? decode(list) : List.of();
        } catch (ReflectiveOperationException | RuntimeException e) { return List.of(); }
    }

    static List<ThermalConsumables.Status> decode(List<?> entries) throws ReflectiveOperationException {
        List<ThermalConsumables.Status> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry == null) continue;
            Class<?> type = entry.getClass();
            Object group = type.getField("group").get(entry);
            if (!(group instanceof Enum<?> value)) continue;
            String suffix = switch (value.name()) { case "FOOD" -> "food"; case "DRINK" -> "drink"; default -> null; };
            int level = type.getField("temperatureLevel").getInt(entry);
            int duration = type.getField("duration").getInt(entry);
            if (suffix == null || level == 0 || level == Integer.MIN_VALUE || duration <= 0) continue;
            result.add(new ThermalConsumables.Status("legendarysurvivaloverhaul:" + (level > 0 ? "hot_" : "cold_") + suffix,
                    "legendarysurvivaloverhaul:" + (level > 0 ? "cold_" : "hot_") + suffix, Math.abs(level) - 1, duration));
        }
        return List.copyOf(result);
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            getConsumable = Class.forName("sfiomn.legendarysurvivaloverhaul.api.data.manager.TemperatureDataManager")
                    .getMethod("getConsumable", ResourceLocation.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }
}
