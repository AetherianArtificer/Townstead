package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.TimedTemperatureEffects;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Live FoodData predicates and values, including temperature stored on individual items. */
public final class ColdSweatConsumables {
    public record Effect(float bodyDegrees, int duration, int stackLimit) {}
    private static boolean initialized;
    private static Object setting;
    private static Method get, testItem, testEntity, temperature, duration, stackLimit;
    private ColdSweatConsumables() {}

    public static List<Effect> effects(ItemStack stack, Entity entity) {
        if (!ModCompat.isLoaded("cold_sweat")) return List.of();
        init();
        if (setting == null) return List.of();
        try {
            Object raw = get.invoke(setting);
            if (!(raw instanceof Multimap<?, ?> map)) return List.of();
            List<Effect> effects = new ArrayList<>();
            for (var entry : map.entries()) {
                if (entry.getKey() != stack.getItem()) continue;
                Object data = entry.getValue();
                if (!Boolean.TRUE.equals(testItem.invoke(data, stack))
                        || !Boolean.TRUE.equals(testEntity.invoke(data, entity))) continue;
                double points = ((Number) temperature.invoke(data, stack, entity)).doubleValue();
                int ticks = ((Number) duration.invoke(data, stack, entity)).intValue();
                int limit = ((Number) stackLimit.invoke(data, stack, entity)).intValue();
                effects.add(new Effect(TimedTemperatureEffects.coldSweatBodyDegrees(points), ticks, limit));
            }
            return List.copyOf(effects);
        } catch (ReflectiveOperationException | RuntimeException e) { return List.of(); }
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Object holder = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings").getField("FOOD_TEMPERATURES").get(null);
            get = holder.getClass().getMethod("get");
            Class<?> data = Class.forName("com.momosoftworks.coldsweat.data.codec.configuration.FoodData");
            testItem = data.getMethod("test", ItemStack.class);
            testEntity = data.getMethod("test", Entity.class);
            temperature = data.getMethod("temperature", ItemStack.class, Entity.class);
            duration = data.getMethod("duration", ItemStack.class, Entity.class);
            stackLimit = data.getMethod("stackLimit", ItemStack.class, Entity.class);
            setting = holder;
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }
}
