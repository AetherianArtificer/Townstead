package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Client-only formatting, using Cold Sweat's live HUD setting when it supplies the reading. */
public final class ThermometerClient {
    private static boolean initialized;
    private static Field units;
    private static Method get;

    private ThermometerClient() {}

    public static void show(ThermometerReadingPayload reading) {
        var player = Minecraft.getInstance().player;
        if (player == null || !Float.isFinite(reading.celsius())) return;
        String text = format(reading.celsius(), fahrenheit(reading.coldSweat()));
        player.displayClientMessage(Component.translatable("townstead.thermometer.reading", text), true);
    }

    private static boolean fahrenheit(boolean coldSweat) {
        if (coldSweat && ModCompat.isLoaded("cold_sweat")) {
            try {
                if (!initialized) {
                    initialized = true;
                    units = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings").getField("UNITS");
                    get = units.getType().getMethod("get");
                }
                if (units != null && get != null) {
                    Object unit = get.invoke(units.get(null));
                    if (unit instanceof Enum<?> value) {
                        if (value.name().equals("F")) return true;
                        if (value.name().equals("C")) return false;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return TownsteadConfig.temperatureInFahrenheit();
    }

    static String format(float celsius, boolean fahrenheit) {
        return String.format(java.util.Locale.ROOT, "%.1f °%s",
                fahrenheit ? celsius * 9d / 5d + 32d : (double) celsius, fahrenheit ? "F" : "C");
    }
}
