package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Townstead's temperature unit and Cold Sweat's are one setting: a change to either is copied to the
 * other and saved. When the game starts, Cold Sweat's own choice wins.
 */
public final class TemperatureUnitSync {
    private static final int INTERVAL = 20;

    private static int ticks;
    private static boolean probed;
    @Nullable private static Object units;
    @Nullable private static Method unitsGet;
    @Nullable private static Method unitsSet;
    @Nullable private static Object useCelsius;
    @Nullable private static Method useCelsiusSet;
    @Nullable private static Method clientSave;
    @Nullable private static Boolean lastTownstead;
    @Nullable private static Boolean lastMod;

    private TemperatureUnitSync() {}

    public static void tick() {
        if (++ticks % INTERVAL != 0 || !ModCompat.isLoaded("cold_sweat") || TownsteadConfig.TEMPERATURE_UNIT == null) return;
        Boolean mod = coldSweatFahrenheit();
        if (mod == null) return;
        boolean townstead = TownsteadConfig.temperatureInFahrenheit();
        if (lastTownstead == null) {
            if (mod != townstead) setTownstead(mod);
            lastTownstead = lastMod = mod;
        } else if (townstead != lastTownstead) {
            setColdSweat(townstead);
            lastTownstead = lastMod = townstead;
        } else if (!mod.equals(lastMod)) {
            setTownstead(mod);
            lastTownstead = lastMod = mod;
        }
    }

    private static void setTownstead(boolean fahrenheit) {
        try {
            TownsteadConfig.TEMPERATURE_UNIT.set(fahrenheit ? TemperatureData.Unit.FAHRENHEIT : TemperatureData.Unit.CELSIUS);
            TownsteadConfig.TEMPERATURE_UNIT.save();
        } catch (RuntimeException e) {
            Townstead.LOGGER.debug("Could not save the temperature unit", e);
        }
    }

    private static @Nullable Boolean coldSweatFahrenheit() {
        if (!probe() || units == null || unitsGet == null) return null;
        try {
            Object unit = unitsGet.invoke(units);
            if (unit instanceof Enum<?> value) {
                if (value.name().equals("F")) return true;
                if (value.name().equals("C")) return false;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setColdSweat(boolean fahrenheit) {
        if (!probe()) return;
        try {
            if (useCelsius != null && useCelsiusSet != null) useCelsiusSet.invoke(useCelsius, !fahrenheit);
            if (clientSave != null) clientSave.invoke(null);
            if (units != null && unitsGet != null && unitsSet != null
                    && unitsGet.invoke(units) instanceof Enum<?> current) {
                unitsSet.invoke(units, Enum.valueOf((Class) current.getDeclaringClass(), fahrenheit ? "F" : "C"));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Townstead.LOGGER.debug("Could not set Cold Sweat's temperature unit", e);
        }
    }

    private static boolean probe() {
        if (probed) return units != null;
        probed = true;
        try {
            Field holder = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings").getField("UNITS");
            units = holder.get(null);
            unitsGet = units.getClass().getMethod("get");
            unitsSet = units.getClass().getMethod("set", Object.class);
            Class<?> client = Class.forName("com.momosoftworks.coldsweat.config.spec.ClientSettingsConfig");
            useCelsius = client.getField("USE_CELSIUS").get(null);
            for (Method method : useCelsius.getClass().getMethods()) {
                if (method.getName().equals("set") && method.getParameterCount() == 1) useCelsiusSet = method;
            }
            clientSave = client.getMethod("save");
        } catch (ReflectiveOperationException | LinkageError e) {
            Townstead.LOGGER.debug("Cold Sweat temperature unit not available", e);
        }
        return units != null;
    }
}
