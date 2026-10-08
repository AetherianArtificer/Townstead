package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Reads Cold Sweat's insulator registry for a worn stack, so garments any mod registers with
 * Cold Sweat warm villagers the way they warm players.
 *
 * <p>Cold Sweat counts insulation in its own units: a wool sweater is 1.5 cold, a cotton shirt
 * is 1 cold and 1 heat. {@link #RESISTANCE_PER_UNIT} maps a unit onto the resistance scale
 * {@link ThermalProtection} shares with LSO, where the same sweater reads 0.5.</p>
 */
public final class ColdSweatInsulators {

    public static final float RESISTANCE_PER_UNIT = 1f / 3f;

    private static boolean initialised;
    private static Method holderGet;
    private static Object insulationItems;
    private static Method insulationOf;
    private static Method getCold;
    private static Method getHeat;

    private ColdSweatInsulators() {}

    public static @Nullable ThermalProtection protection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        initIfNeeded();
        if (insulationItems == null) return null;
        try {
            Object multimap = holderGet.invoke(insulationItems);
            if (multimap == null) return null;
            Object insulators = multimap.getClass().getMethod("get", Object.class).invoke(multimap, stack.getItem());
            if (!(insulators instanceof Collection<?> collection) || collection.isEmpty()) return null;
            double cold = 0, heat = 0;
            for (Object insulator : collection) {
                Object list = insulationOf.invoke(insulator);
                if (!(list instanceof List<?> insulations)) continue;
                for (Object insulation : insulations) {
                    cold += ((Number) getCold.invoke(insulation)).doubleValue();
                    heat += ((Number) getHeat.invoke(insulation)).doubleValue();
                }
            }
            return fromUnits(cold, heat);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static ThermalProtection fromUnits(double cold, double heat) {
        return new ThermalProtection(0, (float) (cold * RESISTANCE_PER_UNIT), (float) (heat * RESISTANCE_PER_UNIT), 0);
    }

    private static synchronized void initIfNeeded() {
        if (initialised) return;
        initialised = true;
        try {
            Class<?> settings = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings");
            Field field = settings.getField("INSULATION_ITEMS");
            insulationItems = field.get(null);
            holderGet = insulationItems.getClass().getMethod("get");
            Class<?> insulatorData = Class.forName("com.momosoftworks.coldsweat.data.codec.configuration.InsulatorData");
            insulationOf = insulatorData.getMethod("insulation");
            Class<?> insulation = Class.forName("com.momosoftworks.coldsweat.api.insulation.Insulation");
            getCold = insulation.getMethod("getCold");
            getHeat = insulation.getMethod("getHeat");
        } catch (ReflectiveOperationException | RuntimeException e) {
            insulationItems = null;
        }
    }
}
