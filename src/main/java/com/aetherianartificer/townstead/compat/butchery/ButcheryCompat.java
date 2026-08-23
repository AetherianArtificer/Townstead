package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Entry point for the optional Butchery mod integration. See
 * {@code docs/design/butchery_integration.md} for the full design.
 *
 * Building-type JSON is served through
 * {@link com.aetherianartificer.townstead.compat.ConditionalCompatPack}; the
 * reflective config bridge here keeps Townstead from taking a compile-time
 * dependency on Butchery.
 */
public final class ButcheryCompat {
    public static final String MOD_ID = "butchery";
    private static final String CONFIG_CLASS =
            "net.mcreator.butchery.configuration.ButcheryconfigConfiguration";

    private ButcheryCompat() {}

    public static boolean isLoaded() {
        return ModCompat.isLoaded(MOD_ID);
    }

    /**
     * Whether Butchery is configured to produce the requested carcass.
     * The field name follows Butchery's registry naming convention: for
     * example, {@code butchery:cow_carcass} maps to {@code COW_CARCASS}.
     * This keeps data-defined slaughter jobs extensible without importing
     * Butchery classes or adding a Java map for every conventional carcass.
     */
    public static boolean carcassEnabled(ResourceLocation carcassId) {
        if (!isLoaded() || carcassId == null || !MOD_ID.equals(carcassId.getNamespace())) return false;
        String path = carcassId.getPath();
        if (!path.endsWith("_carcass")) return false;
        String field = path.toUpperCase(Locale.ROOT);
        return booleanConfig(field, true);
    }

    private static boolean booleanConfig(String fieldName, boolean fallback) {
        try {
            Class<?> config = Class.forName(CONFIG_CLASS);
            Field field = config.getField(fieldName);
            Object configValue = field.get(null);
            if (configValue == null) return fallback;
            Method get = configValue.getClass().getMethod("get");
            Object value = get.invoke(configValue);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Config registration can lag early compatibility checks. Butchery's
            // shipped defaults enable these carcasses, so remain usable until
            // the live value becomes readable.
            return fallback;
        }
    }
}
